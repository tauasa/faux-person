"""Build intl_companies_domains.txt from Wikidata query results.

usage: python3 -I build_companies_intl.py <companies_intl.csv> <public_suffix_list.dat> <out-dir> <per-country cap> <limit>

The input is q_companies_intl.rq's result: businesses with an English name, an
official website and a headquarters, none of whose countries is the US, and
that Wikidata doesn't type as a university, school, central bank, sports club,
hospital, research institute or public body. Companies are ranked by how many
Wikipedia language editions have an article on them, then by employees, and at
most <per-country cap> are kept per country, up to <limit> in all, so a few
large economies don't fill the list.

One line per company: name|HQ city|country|domain.

Country: the headquarters' country, as its ISO 3166-1 alpha-2 code, and only
when the company lists that country too (Hyundai is KR, not one of the others
Wikidata gives it). Countries that no longer exist are left out by the query;
XK, which isn't ISO, here.

HQ city: the headquarters when Wikidata types it as a human settlement, or else
the settlement it lies in, without a "Metropolitan City of" or similar prefix.
A company whose HQ can't be placed is left out.

Left out by name and domain as well, as for the US list: schools, hospitals,
public bodies, clubs, charities and performing-arts companies, in several
languages, and .edu, .gov, .org, .int and academic domains. Also names that
aren't in Latin letters, and domains under a brand top-level domain
(global.toyota), since the marked domain keeps only the label before the suffix.

Domain: the registrable domain of the official website, from the Public Suffix
List (www.sony.co.jp -> sony.co.jp; sbi.bank.in -> sbi.bank.in, not bank.in).
Marked, a domain keeps its first label (sony.example), so a company whose first
label a higher-ranked company, or any US company in us_companies_domains.txt,
already has is skipped.

Ties in rank are broken on the company's Wikidata id, then its domain, so the
same query result always gives the same file.
"""
import csv
import datetime
import re
import sys
import unicodedata
from collections import Counter
from urllib.parse import urlparse

GENERIC = {"com", "net", "biz", "info"}
NOT_A_BUSINESS = re.compile(
    r"\b(\w*universit\w*|acad[eé]mi\w*|coll[eèé]g\w*|c[ée]gep|school|conservato\w*|akademi\w*|academy|"
    r"gymnasium|observator\w*|institut\w*|hospital\w*|clinic|charité|museum|library|tu|eth|\w*paristech|"
    r"organi[sz]ation|ministry|agency|authority|council|federation|association|society|union|league|"
    r"club|f\.?c\.?|a\.?c\.?|foundation|party|church|government|police|army|navy|air force|commission|"
    r"bureau|directorate|department|parliament|court|embassy|theat(?:re|er)|orchestra|philharmoni\w*|"
    r"symphony|ballet|op[ée]ra(?:-comique)?|festival|airport|olympic|red cross|red crescent|scouts?|"
    r"charity|trust fund|choir|statistic\w*|office of|central bank|reconstruction and development|"
    r"national bank of|bank of (?:england|japan|canada|france|italy|spain|korea|russia|china|israel|"
    r"thailand|mexico|brazil|slovenia)|"
    # The same in other languages: universities, schools, libraries, hospitals,
    # museums, ministries, institutes and national research centres.
    r"univers(?:idad|idade|ité|ität|iteit|ità|itet|ytet|itas)\w*|escuela|école|schule|escola|liceo|"
    r"lycée|biblioth\w*|bibliotec\w*|hôpital|krankenhaus|museo|musée|museu\w*|ministerio|ministère|"
    r"ministerium|ministero|instituto|istituto|politecnico|polytechni\w*|fundación|fundação|fondation|"
    r"stiftung|fondazione|bundes\w*|national library|state library|centre national|narodowy bank|"
    r"nationalbank|národní banka|hrvatska narodna|banka slovenije)\b", re.I)
NOT_A_BUSINESS_SITE = re.compile(r"(^|\.)(europa\.eu|who\.int|un\.org)$")
NOT_A_BUSINESS_DOMAIN = re.compile(r"(^|\.)(edu|gov|gob|gouv|org|int|mil|ac|ed|go)(\.[a-z]{2})?$")
# National banks that are central banks, by their English name.
CENTRAL_BANKS = re.compile(r"^(swiss|austrian|hungarian|croatian|czech|polish|danish|national) national bank$", re.I)
CITY_PREFIX = re.compile(r"^(?:Metropolitan City of|Province of|Stadtbezirk|Ostankinsky District of) (.+)$")
ARRONDISSEMENT = re.compile(r"^\d+(?:st|nd|rd|th|er|e) arrondissement of (.+)$")
NOT_ISO = {"XK"}
LATIN = re.compile(r"^[0-9A-Za-zÀ-ɏ&'’.,()+/ -]+$")


def load_psl(path):
    """The Public Suffix List's rules: plain suffixes, wildcards (*.ck) and exceptions (!www.ck)."""
    rules, wild, exc = set(), set(), set()
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("//"):
            continue
        line = line.encode("idna").decode() if not line.isascii() else line
        if line.startswith("!"):
            exc.add(line[1:])
        elif line.startswith("*."):
            wild.add(line[2:])
        else:
            rules.add(line)
    return rules, wild, exc


def registrable(host, psl):
    """The public suffix plus one label (sony.co.jp), or None for a bare suffix."""
    rules, wild, exc = psl
    labels = host.split(".")
    for i in range(len(labels)):
        cand = ".".join(labels[i:])
        if cand in exc:
            suffix_at = i + 1
            break
        if cand in rules or ".".join(labels[i + 1:]) in wild:
            suffix_at = i
            break
    else:
        suffix_at = len(labels) - 1
    return ".".join(labels[suffix_at - 1:]) if suffix_at >= 1 else None


def domain(url, psl):
    host = (urlparse(url).hostname or "").lower().rstrip(".")
    if host.startswith("www."):
        host = host[4:]
    if not re.fullmatch(r"[a-z0-9.-]+", host or "-") or "." not in host:
        return None
    tld = host.rsplit(".", 1)[1]
    if not (len(tld) == 2 or tld in GENERIC):
        return None
    return registrable(host, psl)


def clean(value):
    """True when value has no control or invisible format characters (Unicode Cc, Cf)."""
    return not any(unicodedata.category(ch) in ("Cc", "Cf") for ch in value)


def city_of(row):
    city = (row["hqName"] if row["hqIsPlace"] == "true" else row["admName"]).strip()
    city = unicodedata.normalize("NFC", city)
    for pattern in (ARRONDISSEMENT, CITY_PREFIX):
        m = pattern.match(city)
        if m:
            city = m.group(1)    # 2nd arrondissement of Paris -> Paris
    return city


def main(src, psl_path, out, cap, limit):
    cap, limit = int(cap), int(limit)
    psl = load_psl(psl_path)
    us = [l.split("|") for l in open(f"{out}/us_companies_domains.txt", encoding="utf-8") if not l.startswith("#")]
    taken = {d.strip().split(".")[0] for *_, d in us}

    best = {}
    for r in csv.DictReader(open(src, encoding="utf-8")):
        name = unicodedata.normalize("NFC", r["name"].strip())
        city = city_of(r)
        iso = r["hqIso"]
        dom = domain(r["site"], psl)
        if not (name and city and dom) or iso in NOT_ISO or iso not in r["isos"].split():
            continue
        if NOT_A_BUSINESS.search(name) or CENTRAL_BANKS.match(name) or NOT_A_BUSINESS_DOMAIN.search(dom) \
                or NOT_A_BUSINESS_SITE.search(dom):
            continue
        if not (LATIN.match(name) and LATIN.match(city) and clean(name) and clean(city)) or "|" in name + city:
            continue
        co = r["co"].rsplit("/", 1)[1]
        # One row per company: its best rank, a .com domain over a country one,
        # then the shortest domain and city, so the choice doesn't depend on row order.
        cand = ((int(r["links"]), float(r["employees"] or 0)), dom.endswith(".com"), -len(dom), dom, city,
                name, iso, co)
        cur = best.get(co)
        if cur is None or cand[:5] > cur[:5]:
            best[co] = cand

    per_country, out_lines = Counter(), []
    ranked = sorted(best.values(), key=lambda t: (-t[0][0], -t[0][1], int(t[7][1:]), t[3]))
    for _, _, _, dom, city, name, iso, _ in ranked:
        label = dom.split(".")[0]
        if label in taken or per_country[iso] >= cap:
            continue
        taken.add(label)
        per_country[iso] += 1
        out_lines.append(f"{name}|{city}|{iso}|{dom}")
        if len(out_lines) >= limit:
            break

    head = ("# name|city|country|domain; country is the ISO 3166-1 alpha-2 code of the headquarters; Wikidata "
            "(CC0), ranked by how many Wikipedia language editions have an article on the company; "
            f"built {datetime.date.today().isoformat()}")
    path = f"{out}/intl_companies_domains.txt"
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join([head] + out_lines) + "\n")
    print(path, len(out_lines), "companies in", len(per_country), "countries")


if __name__ == "__main__":
    main(*sys.argv[1:6])
