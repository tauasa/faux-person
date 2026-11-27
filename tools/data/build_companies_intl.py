"""Build intl_companies_domains.txt from Wikidata query results.

usage: python3 -I build_companies_intl.py <companies_intl.csv> <out-dir> <per-country cap> <limit>

The input is q_companies_intl.rq's result: businesses outside the US with an
English name, an official website and, where Wikidata has one, a headquarters.
Companies are ranked by how many Wikipedia language editions have an article on
them, then by employees, and at most <per-country cap> are kept per country, up
to <limit> in all, so a few large economies don't fill the list.

One line per company: name|HQ city|country|domain, the country as its ISO
3166-1 alpha-2 code.

HQ city: the headquarters when Wikidata types it as a human settlement, or else
the settlement it lies in. A company whose HQ can't be placed is left out.

Left out, as for the US list: names of schools, hospitals, public bodies,
clubs and charities, and .edu, .gov, .org, .int and academic domains. Also
names that aren't in Latin letters, and domains whose top level is neither a
country code nor .com, .net, .biz or .info (global.toyota), since the marked
domain keeps only the name before the suffix.

Domain: the registrable part of the official website (www.sony.co.jp ->
sony.co.jp). Marked, a domain keeps its first label (sony.example), so a
company whose first label a higher-ranked company, or any US company in
us_companies_domains.txt, already has is skipped.
"""
import csv
import datetime
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from urllib.parse import urlparse

GENERIC = {"com", "net", "biz", "info"}
SECOND = {"co", "com", "net", "or", "ne", "ac", "go", "gov", "edu", "org", "mil", "gob", "gouv"}
NOT_A_BUSINESS = re.compile(
    r"\b(\w*universit\w*|acad[eé]mi\w*|coll[eèé]g\w*|c[ée]gep|school|conservatoir\w*|conservatorio|akademi\w*|academia|gymnasium|observator\w*|institut\w*|academy|hospital|clinic|museum|library|"
    r"organi[sz]ation|ministry|agency|authority|council|federation|association|society|union|league|"
    r"club|f\.?c\.?|foundation|party|church|government|police|army|navy|air force|commission|bureau|"
    r"department|parliament|court|embassy|theat(?:re|er)|orchestra|festival|airport|olympic|"
    r"red cross|red crescent|scouts?|charity|trust fund|choir|statistic\w*|office of|central bank|national bank of|bank of (?:england|japan|"
    r"canada|france|italy|spain|korea|russia|china|israel|thailand|mexico|brazil)|"
    # The same in other languages: universities, schools, libraries, hospitals,
    # museums, ministries and institutes.
    r"univers(?:idad|idade|ité|ität|iteit|ità|itet|ytet|itas)\w*|escuela|école|schule|escola|liceo|lycée|"
    r"biblioth\w*|bibliotec\w*|bibliothek|hôpital|hospital\w*|krankenhaus|museo|musée|museu\w*|"
    r"ministerio|ministère|ministerium|ministero|instituto|istituto|politecnico|polytechni\w*|"
    r"fundación|fundação|fondation|stiftung|fondazione|bundes\w*|national library|state library)\b", re.I)
NOT_A_BUSINESS_SITE = re.compile(r"(^|\.)(europa\.eu|who\.int|un\.org)$")
ARRONDISSEMENT = re.compile(r"^\d+(?:st|nd|rd|th|er|e) arrondissement of (.+)$")
NOT_A_BUSINESS_DOMAIN = re.compile(r"(^|\.)(edu|gov|gob|gouv|org|int|mil|ac|ed|go)(\.[a-z]{2})?$")
LATIN = re.compile(r"^[0-9A-Za-zÀ-ɏ&'’.,()+/ -]+$")


def domain(url):
    host = (urlparse(url).hostname or "").lower().rstrip(".")
    if host.startswith("www."):
        host = host[4:]
    parts = host.split(".")
    if len(parts) < 2 or not re.fullmatch(r"[a-z0-9.-]+", host):
        return None
    tld = parts[-1]
    if not (len(tld) == 2 or tld in GENERIC):
        return None
    if len(parts) >= 3 and len(tld) == 2 and parts[-2] in SECOND:
        return ".".join(parts[-3:])
    return ".".join(parts[-2:])


def clean(value):
    """True when value has no control or invisible format characters (Unicode Cc, Cf)."""
    return not any(unicodedata.category(ch) in ("Cc", "Cf") for ch in value)


def main(src, out, cap, limit):
    cap, limit = int(cap), int(limit)
    us = [l.split("|") for l in open(f"{out}/us_companies_domains.txt", encoding="utf-8") if not l.startswith("#")]
    taken = {d.strip().split(".")[0] for *_, d in us}

    best = {}
    for r in csv.DictReader(open(src, encoding="utf-8")):
        name = unicodedata.normalize("NFC", r["name"].strip())
        city = (r["hqName"] if r["hqIsPlace"] == "true" else r["admName"]).strip()
        city = unicodedata.normalize("NFC", city)
        m = ARRONDISSEMENT.match(city)
        if m:
            city = m.group(1)    # 2nd arrondissement of Paris -> Paris
        dom = domain(r["site"])
        if not (name and city and dom) or NOT_A_BUSINESS.search(name) or NOT_A_BUSINESS_DOMAIN.search(dom) \
                or NOT_A_BUSINESS_SITE.search(dom):
            continue
        if not (LATIN.match(name) and LATIN.match(city) and clean(name) and clean(city)) or "|" in name + city:
            continue
        rank = (int(r["links"]), float(r["employees"] or 0))
        key = r["co"]
        # One row per company: its best rank, and a .com domain over a country one.
        cur = best.get(key)
        cand = (rank, dom.endswith(".com"), name, city, r["iso"], dom)
        if cur is None or cand[:2] > cur[:2]:
            best[key] = cand

    per_country, out_lines = Counter(), []
    for rank, _, name, city, iso, dom in sorted(best.values(), key=lambda t: (-t[0][0], -t[0][1], t[2])):
        label = dom.split(".")[0]
        if label in taken or per_country[iso] >= cap:
            continue
        taken.add(label)
        per_country[iso] += 1
        out_lines.append(f"{name}|{city}|{iso}|{dom}")
        if len(out_lines) >= limit:
            break

    head = ("# name|city|country|domain; country is the ISO 3166-1 alpha-2 code; Wikidata (CC0), ranked by how "
            "many Wikipedia language editions have an article on the company; "
            f"built {datetime.date.today().isoformat()}")
    path = f"{out}/intl_companies_domains.txt"
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join([head] + out_lines) + "\n")
    print(path, len(out_lines), "companies in", len(per_country), "countries")


if __name__ == "__main__":
    main(*sys.argv[1:5])
