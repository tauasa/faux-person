"""Build us_cities_states_zip.txt from GeoNames.

usage: python3 -I build_cities.py <geonames-dir> <out-dir>

Per state: the 10 most populous places that have a ZIP code, plus 15 picked
at random (fixed seed) from the rest, each with one of its ZIP codes.
Also writes counties.tsv (state, county FIPS, county name, biggest city) for
the street builder.
"""
import datetime
import random
import sys
import unicodedata
from collections import defaultdict

STATES = ("AL AK AZ AR CA CO CT DE FL GA HI ID IL IN IA KS KY LA ME MD MA MI MN MS MO "
          "MT NE NV NH NJ NM NY NC ND OH OK OR PA RI SC SD TN TX UT VT VA WA WV WI WY").split()
TOP, RANDOM = 10, 15
rng = random.Random(20261119)


def clean(value):
    """True when value has no control or invisible format characters (Unicode Cc, Cf)."""
    return not any(unicodedata.category(ch) in ("Cc", "Cf") for ch in value)


def main(src, out):
    zips = defaultdict(list)          # (state, city lower) -> [zip]
    county = {}                       # (state, city lower) -> (county fips, county name)
    for line in open(f"{src}/zip/US.txt", encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        st, city, z = f[4], f[2], f[1]
        if st in STATES and clean(city) and z.isdigit():
            zips[(st, city.lower())].append(z)
            if f[6]:
                county.setdefault((st, city.lower()), (f[6], f[5]))

    places = defaultdict(dict)        # state -> city -> population
    for line in open(f"{src}/cities/cities1000.txt", encoding="utf-8"):
        f = line.split("\t")
        if f[8] != "US" or f[10] not in STATES or f[6] != "P":
            continue
        for name in (f[1], f[2]):
            if (f[10], name.lower()) in zips:
                places[f[10]][name] = max(int(f[14] or 0), places[f[10]].get(name, 0))
                break

    lines, counties = [], []
    for st in STATES:
        ranked = sorted(places[st].items(), key=lambda t: -t[1])
        chosen = [c for c, _ in ranked[:TOP]]
        rest = [c for c, _ in ranked[TOP:]]
        chosen += rng.sample(rest, min(RANDOM, len(rest)))
        if len(chosen) < TOP + RANDOM:  # small states: fill from ZIP-only places
            extra = sorted({c for (s, c) in zips if s == st} - {c.lower() for c in chosen})
            chosen += [e.title() for e in rng.sample(extra, TOP + RANDOM - len(chosen))]
        for c in chosen:
            lines.append(f"{c}|{st}|{rng.choice(sorted(zips[(st, c.lower())]))}")
        for c, _ in ranked[:3]:
            fips, name = county.get((st, c.lower()), ("", ""))
            counties.append(f"{st}\t{fips}\t{name}\t{c}")
    lines.sort(key=lambda l: (l.split("|")[1], l.lower()))
    with open(f"{out}/us_cities_states_zip.txt", "w", encoding="utf-8", newline="\n") as fh:
        fh.write(f"# city|state|zip; GeoNames (CC BY 4.0), see NOTICE; built {datetime.date.today().isoformat()}\n")
        fh.write("\n".join(lines) + "\n")
    with open(f"{out}/counties.tsv", "w", encoding="utf-8") as fh:
        fh.write("\n".join(counties) + "\n")
    per = defaultdict(int)
    for l in lines:
        per[l.split("|")[1]] += 1
    print(len(lines), "lines; min per state", min(per.values()), "states", len(per))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
