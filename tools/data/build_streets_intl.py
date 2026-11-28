"""Build intl_streets.txt: addresses for people who live outside the US.

usage: python3 -I build_streets_intl.py <streets_intl.csv> <cities1000.txt> <postal allCountries.txt>
                                        <intl_companies_domains.txt> <out-dir> <per-country> <min-streets>

A person lives in their employer's country, so there's a line for every country
in intl_companies_domains.txt. One line per street: country|street|city|postal.

Streets: q_streets_intl.rq's result, Wikidata (CC0) streets, avenues and
boulevards with coordinates. Each gets one name, in the first of these it has:
its country's own language, from OWN below (Rua dos Adobes, not Adobes
Street); its "mul" label; its English label; its other labels in the query's
language order. OWN lists the countries with one main language written in
Latin letters. The rest, such as Japan, India, Britain or Belgium, start from
"mul" and English. OWN is a fixed list because bots copy labels into other
languages wholesale: by count alone, Japan's streets would be named in French. A name with characters outside Latin letters, digits and common
punctuation is dropped, and so is anything in brackets after it. A street in
more than one country (a border road) is left out.

City: the nearest GeoNames populated place (CC BY 4.0, cities1000) in the same
country with 15,000 people or more, within 15 km, so a London street is in
London rather than one of its districts; else the nearest of any size within
25 km. Sections of places and historical, abandoned or destroyed ones are left
out. A street with neither is dropped.

Postal code: the nearest GeoNames postal code point in the same country, within
10 km, leaving out French CEDEX codes, which are for businesses' mail, or empty
when there's none (GeoNames has postal codes for about 120
countries, and for some, such as GB and CA, only the first part).

Each country keeps at most <per-country> streets: its named streets are
shuffled (fixed seed) and taken in that order, skipping one that can't be
placed or whose name its city already has. A country with fewer than
<min-streets> placed streets gets city lines instead, with an empty street: its
<per-country> most populous places, each with its nearest postal code.
"""
import csv
import datetime
import math
import random
import re
import sys
from collections import defaultdict

csv.field_size_limit(10_000_000)
LANG_ORDER = ["en", "mul", "fr", "de", "es", "it", "pt", "nl", "sv", "nb", "nn", "da", "fi", "pl", "cs", "sk",
              "hu", "ro", "hr", "sl", "tr", "id", "ms", "vi", "tl", "sq", "et", "lv", "lt", "ca", "eu", "gl", "is",
              "ga", "cy", "af", "sw", "lb", "mt", "az", "uz", "tk"]
RANK = {lang: i for i, lang in enumerate(LANG_ORDER)}
LATIN = re.compile(r"^[0-9A-Za-zÀ-ɏḀ-ỿ'’.\- ]+$")
BRACKETS = re.compile(r"\s*[(\[].*$")
POINT = re.compile(r"^POINT\(\s*(-?[0-9.]+)\s+(-?[0-9.]+)\s*\)$", re.I)
NOT_A_PLACE = {"PPLX", "PPLH", "PPLQ", "PPLW", "PPLCH"}
CITY_KM = 25
OWN = {
    "AL": "sq", "AO": "pt", "AR": "es", "AT": "de", "BF": "fr", "BI": "fr", "BJ": "fr", "BO": "es", "BR": "pt",
    "CD": "fr", "CG": "fr", "CI": "fr", "CL": "es", "CM": "fr", "CO": "es", "CR": "es", "CU": "es", "CV": "pt",
    "CZ": "cs", "DE": "de", "DK": "da", "DO": "es", "EC": "es", "EE": "et", "ES": "es", "FI": "fi", "FR": "fr",
    "GA": "fr", "GN": "fr", "GT": "es", "GW": "pt", "HN": "es", "HR": "hr", "HU": "hu", "ID": "id", "IS": "is",
    "IT": "it", "LT": "lt", "LV": "lv", "MC": "fr", "MG": "fr", "ML": "fr", "MX": "es", "MZ": "pt", "NE": "fr",
    "NI": "es", "NL": "nl", "NO": "nb", "PA": "es", "PE": "es", "PL": "pl", "PT": "pt", "PY": "es", "RO": "ro",
    "SE": "sv", "SI": "sl", "SK": "sk", "SN": "fr", "ST": "pt", "SV": "es", "TG": "fr", "TR": "tr", "UY": "es",
    "VE": "es", "VN": "vi",
}
TOWN_KM = 15
TOWN = 15_000
POSTAL_KM = 10
rng = random.Random(20261128)


def km(a, b):
    """Great-circle distance between two (lat, lon) points."""
    lat1, lon1, lat2, lon2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 6371 * 2 * math.asin(min(1, math.sqrt(h)))


class Grid:
    """Points by country in one-degree cells, for nearest-within-a-distance lookups."""

    def __init__(self):
        self.cells = defaultdict(list)

    def add(self, iso, lat, lon, value):
        self.cells[(iso, math.floor(lat), math.floor(lon))].append((lat, lon, value))

    def nearest(self, iso, lat, lon, limit_km):
        best, best_d = None, limit_km
        for dlat in (-1, 0, 1):
            for dlon in (-1, 0, 1):
                for plat, plon, value in self.cells.get((iso, math.floor(lat) + dlat, math.floor(lon) + dlon), ()):
                    d = km((lat, lon), (plat, plon))
                    if d <= best_d:
                        best, best_d = value, d
        return best


def street_name(labels):
    for lang, label in sorted(labels, key=lambda l: (RANK[l[0]], l[1])):
        name = BRACKETS.sub("", label).strip()
        if name and LATIN.match(name) and any(ch.isalpha() for ch in name):
            return name
    return None


def main(streets_csv, cities_txt, postal_txt, companies, out, per_country, min_streets):
    per_country, min_streets = int(per_country), int(min_streets)
    wanted = set()
    for line in open(companies, encoding="utf-8"):
        if line.strip() and not line.startswith("#"):
            wanted.add(line.rstrip("\n").split("|")[2])

    places, towns, biggest = Grid(), Grid(), defaultdict(list)
    for line in open(cities_txt, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        iso, code = f[8], f[7]
        if iso not in wanted or code in NOT_A_PLACE or not LATIN.match(f[2]):
            continue
        lat, lon = float(f[4]), float(f[5])
        places.add(iso, lat, lon, f[2])
        if int(f[14] or 0) >= TOWN:
            towns.add(iso, lat, lon, f[2])
        biggest[iso].append((-int(f[14] or 0), f[2], lat, lon))

    # One point per postal code and hundredth of a degree: GeoNames lists a code
    # once for each place it covers, and nearby repeats add nothing.
    postal, seen_postal = Grid(), set()
    for line in open(postal_txt, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if f[0] in wanted and f[9] and f[10] and f[1].strip() and "CEDEX" not in f[1].upper():
            lat, lon = round(float(f[9]), 2), round(float(f[10]), 2)
            key = (f[0], f[1].strip(), lat, lon)
            if key not in seen_postal:
                seen_postal.add(key)
                postal.add(f[0], lat, lon, f[1].strip())
    seen_postal = None

    # Per street, only what's needed: its country (or None once it has two),
    # where it is, and its usable name in each language.
    streets = {}
    with open(streets_csv, encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            m = POINT.match(row["coord"])
            if not m or row["lang"] not in RANK or row["iso"] not in wanted:
                continue
            s = int(row["s"].rsplit("/", 1)[-1][1:])
            known = streets.get(s)
            if known is None:
                known = streets[s] = [row["iso"], float(m.group(2)), float(m.group(1)), {}]
            elif known[0] != row["iso"]:
                known[0] = None
            name = street_name([(row["lang"], row["label"])])
            if name:
                known[3][row["lang"]] = name

    # Each country's named streets in Wikidata id order, so the shuffle below
    # gives the same picks from the same query result.
    by_country = defaultdict(list)
    for s in sorted(streets):
        iso, lat, lon, names = streets[s]
        if iso is None or not names:
            continue
        order = [OWN.get(iso), "mul", "en"] + LANG_ORDER
        name = names[next(lang for lang in order if lang in names)]
        if name is not None:
            by_country[iso].append((name, lat, lon))
    streets = None

    lines, by_street, by_city = [], 0, []
    for iso in sorted(wanted):
        candidates = by_country[iso]
        rng.shuffle(candidates)
        found = {}
        for name, lat, lon in candidates:
            city = towns.nearest(iso, lat, lon, TOWN_KM) or places.nearest(iso, lat, lon, CITY_KM)
            if not city or (name.lower(), city.lower()) in found:
                continue
            found[(name.lower(), city.lower())] = (name, city, postal.nearest(iso, lat, lon, POSTAL_KM) or "")
            if len(found) == per_country:
                break
        if len(found) >= min_streets:
            picks = list(found.values())
            by_street += 1
        else:
            seen, picks = set(), []
            for _, city, lat, lon in sorted(biggest[iso]):
                if city.lower() not in seen:
                    seen.add(city.lower())
                    picks.append(("", city, postal.nearest(iso, lat, lon, POSTAL_KM) or ""))
                if len(picks) == per_country:
                    break
            by_city.append(iso)
        if not picks:
            raise SystemExit(f"no places at all for {iso}")
        lines += [f"{iso}|{street}|{city}|{code}" for street, city, code in picks]
    lines.sort(key=lambda l: [p.lower() for p in l.split("|")])
    with open(f"{out}/intl_streets.txt", "w", encoding="utf-8", newline="\n") as fh:
        fh.write("# country|street|city|postal; streets from Wikidata (CC0), city and postal code from GeoNames "
                 f"(CC BY 4.0), see NOTICE; an empty street means the country has a city line only; built "
                 f"{datetime.date.today().isoformat()}\n")
        fh.write("\n".join(lines) + "\n")
    print(len(lines), "lines;", by_street, "countries with streets;", len(by_city), "with cities only:",
          " ".join(by_city))


if __name__ == "__main__":
    main(*sys.argv[1:8])
