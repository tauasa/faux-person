"""Build us_companies_domains.txt from Wikidata query results.

usage: python3 -I build_companies.py <out-dir> <limit> <geonames cities1000.txt> <geonames zip US.txt> <by-revenue.csv> [<supplement.csv>]

Companies come first from by-revenue.csv (US businesses ranked by the highest
USD revenue Wikidata holds), then from the supplement (NYSE/Nasdaq-listed US
businesses with 5,000+ employees and no revenue on Wikidata), up to <limit>.

One line per company: name|HQ city|state code|domain.

HQ city: Wikidata's headquarters, when its name is a GeoNames place within
50 km of its coordinates. A building, campus or neighbourhood resolves to the
nearest place of 20,000+ people within 40 km. With no HQ coordinates, the
company's own coordinates are used the same way, then the HQ's name alone
(most populous match). A company whose HQ can't be placed is left out. A match that is a GeoNames
neighbourhood (feature code PPLX, such as Hell's Kitchen) moves to the most
populous place within 40 km, which is the city it belongs to.
Names with control or invisible format characters are left out too.

Left out: .edu/.org/.gov/.mil domains and names like "University" or
"Foundation", since Wikidata types some of those as businesses.

Domain: the registrable part of the official website
(corporate.exxonmobil.com -> exxonmobil.com), preferring .com. A domain
already taken by a higher-ranked company is skipped.
"""
import datetime
import csv
import math
import re
import sys
import unicodedata
from collections import Counter, OrderedDict
from urllib.parse import urlparse

ABBR = {"Alabama": "AL", "Alaska": "AK", "Arizona": "AZ", "Arkansas": "AR", "California": "CA",
        "Colorado": "CO", "Connecticut": "CT", "Delaware": "DE", "Florida": "FL", "Georgia": "GA",
        "Hawaii": "HI", "Idaho": "ID", "Illinois": "IL", "Indiana": "IN", "Iowa": "IA", "Kansas": "KS",
        "Kentucky": "KY", "Louisiana": "LA", "Maine": "ME", "Maryland": "MD", "Massachusetts": "MA",
        "Michigan": "MI", "Minnesota": "MN", "Mississippi": "MS", "Missouri": "MO", "Montana": "MT",
        "Nebraska": "NE", "Nevada": "NV", "New Hampshire": "NH", "New Jersey": "NJ", "New Mexico": "NM",
        "New York": "NY", "North Carolina": "NC", "North Dakota": "ND", "Ohio": "OH", "Oklahoma": "OK",
        "Oregon": "OR", "Pennsylvania": "PA", "Rhode Island": "RI", "South Carolina": "SC",
        "South Dakota": "SD", "Tennessee": "TN", "Texas": "TX", "Utah": "UT", "Vermont": "VT",
        "Virginia": "VA", "Washington": "WA", "West Virginia": "WV", "Wisconsin": "WI", "Wyoming": "WY"}
# Wikidata types some universities, charities and agencies as businesses.
NON_COMMERCIAL_TLDS = (".edu", ".org", ".gov", ".mil")
NOT_A_COMPANY = re.compile(r"\b(University|College|School|Schools|Society|Foundation|Association|Hospital|"
                           r"Museum|Church|Diocese|Charities|Council|Agency|Department|Authority|Commission|"
                           r"Institute|Academy|Library|Orchestra|Opera|YMCA|YWCA|Postal Service)\b", re.I)
POINT = re.compile(r"POINT\(([-0-9.eE]+) ([-0-9.eE]+)\)")


def domain(url):
    host = (urlparse(url).hostname or "").lower().rstrip(".")
    parts = host.split(".")
    if len(parts) < 2 or not re.fullmatch(r"[a-z0-9.-]+", host):
        return None
    if len(parts) >= 3 and len(parts[-1]) == 2 and parts[-2] in ("co", "com", "org", "net"):
        return ".".join(parts[-3:])
    return ".".join(parts[-2:])


def clean(value):
    """True when value has no control or invisible format characters (Unicode Cc, Cf)."""
    return not any(unicodedata.category(ch) in ("Cc", "Cf") for ch in value)


def load_places(path):
    """GeoNames US populated places: (name, state, lat, lon, population, feature code)."""
    out = []
    for line in open(path, encoding="utf-8"):
        f = line.split("\t")
        if f[8] == "US" and f[10] in ABBR.values() and f[6] == "P":
            out.append((f[1], f[10], float(f[4]), float(f[5]), int(f[14] or 0), f[7]))
    return out


def km(p, lat, lon):
    dlat, dlon = math.radians(p[2] - lat), math.radians(p[3] - lon)
    a = (math.sin(dlat / 2) ** 2
         + math.cos(math.radians(lat)) * math.cos(math.radians(p[2])) * math.sin(dlon / 2) ** 2)
    return 6371 * 2 * math.asin(math.sqrt(a))


POSTAL = set()  # (place name, state) the USPS uses as a mailing city; filled in main()


def parent_city(places, p):
    """For a neighbourhood (GeoNames PPLX: Hell's Kitchen, the Chicago Loop),
    the most populous real place within 40 km, which is the city it's part of.
    GeoNames also tags some real cities PPLX (Beverly Hills, Culver City), so a
    PPLX place the USPS uses as a mailing city (Beverly Hills, La Jolla) stays.
    Anything else is returned as it is."""
    if p is None or p[5] != "PPLX" or (p[0].lower(), p[1]) in POSTAL:
        return p
    near = [q for q in places if q[5] != "PPLX" and abs(q[2] - p[2]) < 0.5 and abs(q[3] - p[3]) < 0.6
            and km(q, p[2], p[3]) <= 40]
    return max(near, key=lambda q: q[4]) if near else p


def resolve(places, by_name, hq_name, point):
    return parent_city(places, _resolve(places, by_name, hq_name, point))


def _resolve(places, by_name, hq_name, point):
    if point is None:
        return by_name.get(hq_name)
    lon, lat = float(point.group(1)), float(point.group(2))
    near = [(km(p, lat, lon), p) for p in places if abs(p[2] - lat) < 0.6 and abs(p[3] - lon) < 0.8]
    named = [t for t in near if t[1][0] == hq_name and t[0] <= 50]
    if named:
        return min(named)[1]
    big = [t for t in near if t[1][4] >= 20000 and t[0] <= 40]
    if big:
        return min(big)[1]
    small = [t for t in near if t[0] <= 15]
    return min(small)[1] if small else None


def companies(path, places, by_name):
    cos = OrderedDict()
    for r in csv.DictReader(open(path, encoding="utf-8")):
        c = cos.setdefault(r["co"], {"name": r["name"], "rank": float(r["rev"]),
                                    "domains": Counter(), "hq": Counter()})
        d = domain(r["site"])
        if d:
            c["domains"][d] += 1
        hq = r.get("hqName") or ""
        pt = POINT.match(r.get("coord") or "") or POINT.match(r.get("coCoord") or "")
        place = resolve(places, by_name, hq, pt)
        if place:
            c["hq"][(place[0], place[1])] += 1
    return sorted(cos.values(), key=lambda c: -c["rank"])


def main(out, limit, places_path, postal_path, *sources):
    for line in open(postal_path, encoding="utf-8"):
        f = line.split("\t")
        POSTAL.add((f[2].lower(), f[4]))
    places = load_places(places_path)
    by_name = {}
    for p in places:
        if p[0] not in by_name or p[4] > by_name[p[0]][4]:
            by_name[p[0]] = p
    lines, seen, dropped = [], set(), []
    for src in sources:
        for c in companies(src, places, by_name):
            if len(lines) >= limit:
                break
            if not c["domains"] or not c["hq"] or "|" in c["name"] or not clean(c["name"]):
                dropped.append(c["name"])
                continue
            if NOT_A_COMPANY.search(c["name"]):
                continue
            ok = [d for d in c["domains"] if not d.endswith(NON_COMMERCIAL_TLDS)]
            if not ok:
                continue
            d = max(ok, key=lambda d: (d.endswith(".com"), c["domains"][d], -len(d)))
            if d in seen:
                continue
            seen.add(d)
            city, st = c["hq"].most_common(1)[0][0]
            lines.append(f"{c['name']}|{city}|{st}|{d}")
    with open(f"{out}/us_companies_domains.txt", "w", encoding="utf-8", newline="\n") as fh:
        fh.write("# name|city|state|domain; Wikidata (CC0), headquarters placed with GeoNames (CC BY 4.0), "
                 f"see NOTICE; built {datetime.date.today().isoformat()}\n")
        fh.write("\n".join(lines) + "\n")
    print(len(lines), "companies;", len(dropped), "left out for no placeable HQ, e.g.", dropped[:12])


if __name__ == "__main__":
    main(sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4], *sys.argv[5:])
