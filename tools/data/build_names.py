"""Build the six name files from Wikidata query results (QLever CSV).

usage: python3 -I build_names.py <wd-csv-dir> <out-dir>

Each country's names are ranked by how many people with that citizenship
carry them on Wikidata. Countries are interleaved rank by rank (core
countries take more per round), so every country in a region contributes.
A name joins the asia or rus list only if its share of that region's people
is at least half its share of the western region's, which keeps out Western
names (Joseph, Andrew) without dropping Wang, Patel or Anna.
"""
import csv
import re
import sys
import unicodedata
from collections import defaultdict

TARGET = 1200
VALID = re.compile(r"^[A-ZÀ-ɏ][A-Za-zÀ-ɏ'’-]*[a-zÀ-ɏ]$")
GENDER = {"Q12308941": "M", "Q11879590": "F", "Q3409032": "U"}
WEIGHT = {"Q159": 3, "Q15180": 3, "Q34266": 2, "Q212": 2, "Q184": 2, "Q232": 2,
          "Q148": 3, "Q668": 3, "Q30": 3, "Q145": 2, "Q96": 2, "Q155": 2}


def fold(s):
    return unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()


def load(path, ascii_only=False, canon=lambda n: n):
    """-> (by_country ranked names, gender totals, name share of region)."""
    by_country = defaultdict(list)
    gtot = defaultdict(lambda: defaultdict(int))
    count = defaultdict(int)
    for r in csv.DictReader(open(path, encoding="utf-8")):
        name = fold(r["name"]) if ascii_only else r["name"]
        if not VALID.match(name):
            continue
        name = canon(name)
        c = r["c"].rsplit("/", 1)[1]
        n = int(r["n"])
        g = GENDER[r["type"].rsplit("/", 1)[1]] if "type" in r else "U"
        by_country[c].append((n, name))
        gtot[name][g] += n
        count[name.lower()] += n
    total = sum(count.values())
    share = {k: v / total for k, v in count.items()}
    ranked = {}
    for c, v in by_country.items():
        agg = defaultdict(int)
        for n, name in v:
            agg[name] += n
        ranked[c] = [name for name, _ in sorted(agg.items(), key=lambda t: -t[1])]
    return ranked, gtot, share


def interleave(by_country, limit, seen):
    out, cur = [], {c: 0 for c in by_country}
    key = str.lower
    while len(out) < limit and any(cur[c] < len(v) for c, v in by_country.items()):
        for c in sorted(by_country):
            v = by_country[c]
            for _ in range(WEIGHT.get(c, 1)):
                while cur[c] < len(v) and key(v[cur[c]]) in seen:
                    cur[c] += 1
                if cur[c] < len(v) and len(out) < limit:
                    seen.add(key(v[cur[c]]))
                    out.append(v[cur[c]])
                    cur[c] += 1
    return out


def not_western(share, west_share, factor=0.5):
    def ok(name):
        k = name.lower()
        return share.get(k, 0) >= factor * max(west_share.get(k, 0), west_share.get(fold(k), 0))
    return ok


def first_names(path, ascii_only=False, keep=lambda n: True):
    ranked, gtot, share = load(path, ascii_only)

    def gender(name):
        g = gtot[name]
        m, f = g["M"], g["F"]
        if g["U"] >= max(m, f) or (m and f and min(m, f) / (m + f) >= 0.3):
            return "U"
        return "M" if m >= f else "F"

    # Half the list from names women carry and half from names men carry,
    # so Wikidata's male skew doesn't carry into the file.
    seen, chosen = set(), []
    for want in ("F", "M"):
        sub = {c: [n for n in v if gender(n) in (want, "U") and keep(n)] for c, v in ranked.items()}
        chosen += interleave(sub, TARGET // 2, seen)
    return sorted(((n, gender(n)) for n in chosen), key=lambda t: t[0].lower()), share


# Russian-style masculine -> feminine surname forms, and back.
FEM_RULES = [(r"(sk|tsk|zk)(iy|y)$", r"\1aya"), (r"(sk|ck|dzk)i$", r"\1a"), (r"(ov|ev|yov|yev|in|yn)$", r"\1a"),
             (r"oy$", "aya"), (r"iy$", "aya")]
MASC_RULES = [(r"(sk|tsk|zk)aya$", r"\1iy"), (r"(sk|ck|dzk)a$", r"\1i"), (r"(ov|ev|yov|yev|in|yn)a$", r"\1"), (r"aya$", "iy")]


def feminine(m):
    for pat, rep in FEM_RULES:
        if re.search(pat, m):
            return re.sub(pat, rep, m)
    return m


def masculine(f):
    for pat, rep in MASC_RULES:
        if re.search(pat, f):
            return re.sub(pat, rep, f)
    return f


# Feminine forms seen in Central/Eastern European data, and how to get the
# masculine form back: Petrova -> Petrov, Kowalska -> Kowalski,
# Novakova -> Novak, Svobodova -> Svoboda, Dvorska -> Dvorsky.
FEM_TO_MASC = [(r"(ov|ev|in|yn)a$", [r"\1"]), (r"(sk|ck|dzk)a$", [r"\1i"]),
               (r"(sk|ck)á$", [r"\1ý"]), (r"ová$", ["", "a"]), (r"skaya$", ["skiy", "sky"])]


def pair_genders(names, pool):
    """[(masculine, feminine)]: a feminine form whose masculine form is also in
    the data is folded into it, and the feminine form is kept only when the
    data shows it."""
    lower = {n.lower(): n for n in pool}
    masc_of = {}
    for n in pool:
        for pat, reps in FEM_TO_MASC:
            if re.search(pat, n):
                for rep in reps:
                    m = lower.get(re.sub(pat, rep, n).lower())
                    if m and m != n:
                        masc_of[n] = m
                        break
            if n in masc_of:
                break
    fem_of = {m: f for f, m in masc_of.items()}
    out = []
    for n in names:
        m = masc_of.get(n, n)
        out.append((m, fem_of.get(m, m)))
    return sorted(dict.fromkeys(out), key=lambda t: t[0].lower())


def surnames(path, ascii_only=False, keep=lambda n: True, canon=lambda n: n, limit=TARGET):
    ranked, _, share = load(path, ascii_only, canon)
    sub = {c: [n for n in v if keep(n)] for c, v in ranked.items()}
    return sorted(interleave(sub, limit, set()), key=str.lower), share


def weights(path, ascii_only=False, canon=lambda n: n):
    """How many people on Wikidata carry each surname, summed over the
    region's countries. A name item can have both an "en" and a "mul" label,
    which the query returns as two identical rows, so identical rows count once."""
    seen, out = set(), defaultdict(int)
    for r in csv.DictReader(open(path, encoding="utf-8")):
        key = (r["c"], r["name"], r["n"])
        if key in seen:
            continue
        seen.add(key)
        name = fold(r["name"]) if ascii_only else r["name"]
        if VALID.match(name):
            out[canon(name)] += int(r["n"])
    return out


def weighted(m, f, counts):
    """A surname line with its weight last: "Kim|13532", "Petrov|Petrova|401".
    A feminine form's count adds to its masculine form's; the weight is at least 1."""
    w = counts.get(m, 0) + (counts.get(f, 0) if f != m else 0)
    return "|".join([m] + ([f] if f != m else []) + [str(max(1, w))])


def write(path, lines):
    # NFC, so a name like "José" is always one code point per letter, whatever
    # form Wikidata stored it in.
    lines = [unicodedata.normalize("NFC", l) for l in lines]
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print(path, len(lines))


def main(src, out):
    west_first, wf_share = first_names(f"{src}/first_W.csv")
    write(f"{out}/western_first_names.txt", [f"{n}|{g}" for n, g in west_first])
    for reg, name in (("A", "asia"), ("R", "rus")):
        _, _, share = load(f"{src}/first_{reg}.csv", reg == "A")
        names, _ = first_names(f"{src}/first_{reg}.csv", reg == "A", not_western(share, wf_share))
        write(f"{out}/{name}_first_names.txt", [f"{n}|{g}" for n, g in names])

    west_ranked, _, _ = load(f"{src}/sur_W.csv")
    west_pool = {n for v in west_ranked.values() for n in v}
    # Folding feminine forms into masculine ones shrinks the list, so select
    # a little more until TARGET distinct masculine forms remain.
    limit = TARGET
    while True:
        west_sur, ws_share = surnames(f"{src}/sur_W.csv", limit=limit)
        pairs = pair_genders(west_sur, west_pool)
        if len(pairs) >= TARGET:
            break
        limit += TARGET - len(pairs)
    wc = weights(f"{src}/sur_W.csv")
    write(f"{out}/western_surnames.txt", [weighted(m, f, wc) for m, f in pairs])
    _, _, a_share = load(f"{src}/sur_A.csv", True)
    asia_sur, _ = surnames(f"{src}/sur_A.csv", True, not_western(a_share, ws_share))
    ac = weights(f"{src}/sur_A.csv", True)
    write(f"{out}/asia_surnames.txt", [weighted(n, n, ac) for n in asia_sur])
    _, _, r_share = load(f"{src}/sur_R.csv", canon=masculine)
    rus_sur, _ = surnames(f"{src}/sur_R.csv", keep=not_western(r_share, ws_share), canon=masculine)
    # Counts are folded onto the masculine form, so Ivanova's count is in Ivanov's.
    rc = weights(f"{src}/sur_R.csv", canon=masculine)
    write(f"{out}/rus_surnames.txt",
          ["|".join([m] + ([feminine(m)] if feminine(m) != m else []) + [str(max(1, rc.get(m, 0)))])
           for m in rus_sur])

    rok(src, out, wf_share)
    countries(src, out)
    rok_hangul(src, out)
    more_regions(src, out)


# South Korea: citizens of Q884 only, from the asia query results.
ROK = "Q884"
# Surnames Wikidata lists for South Korean citizens that aren't Korean
# romanizations: Chinese pinyin forms (Korean-Chinese people), a Japanese
# name, and single families.
ROK_NOT_KOREAN = {"Zheng", "Zhang", "Zhao", "Liu", "Xu", "Jiang", "Huang", "Quan", "Yin", "Wu",
                  "Shi", "Dang", "Sun", "Li", "Arai", "Linton", "Young"}


def only(path, country, tmp):
    """Write the rows of one country to tmp, so load() and the selection rules apply unchanged."""
    with open(path, encoding="utf-8") as fin, open(tmp, "w", encoding="utf-8", newline="") as fout:
        r = csv.DictReader(fin)
        w = csv.DictWriter(fout, fieldnames=r.fieldnames)
        w.writeheader()
        for row in r:
            if row["c"].endswith("/" + country):
                w.writerow(row)
    return tmp


def rok(src, out, wf_share):
    first = only(f"{src}/first_A.csv", ROK, f"{src}/first_K.csv")
    _, _, share = load(first, True)
    names, _ = first_names(first, True, not_western(share, wf_share))
    write(f"{out}/rok_first_names.txt", [f"{n}|{g}" for n, g in names])
    # Every surname carried by 3+ South Korean citizens on Wikidata. Korea has
    # only about 280 surnames, so this list is short by nature.
    sur = only(f"{src}/sur_A.csv", ROK, f"{src}/sur_K.csv")
    ranked, _, _ = load(sur, True)
    picked = sorted({n for v in ranked.values() for n in v if n not in ROK_NOT_KOREAN}, key=str.lower)
    kc = weights(sur, True)
    write(f"{out}/rok_surnames.txt", [weighted(n, n, kc) for n in picked])




# Single-country regions, from their own queries (q_first_<X>.rq, q_sur_<X>.rq).
# (file prefix, query suffix, fold to ASCII, regions whose names to keep out, factor)
# "Keep out" uses the same share test as asia and rus: a name stays only if
# its share of this region's people is at least <factor> times its share of
# the other's (0.5 unless given). Hebrew uses 1.0: Israel has many immigrants
# whose names (Barry, Svetlana) are just as common where they came from.
COUNTRIES = [
    ("mex", "MEX", False, []),
    ("irn", "IRN", True, ["W"]),
    ("heb", "ISR", True, ["PSE", "W", "R"], 1.0),  # Israeli citizens, less mainly Palestinian, Western or Russian names
    ("pse", "PSE", True, ["W", "ISR"]),       # Palestine and Palestinian ethnicity, less Western and Israeli names
]
def countries(src, out):
    for prefix, q, ascii_only, avoid, *rest in COUNTRIES:
        factor = rest[0] if rest else 0.5
        fpath, spath = f"{src}/first_{q}.csv", f"{src}/sur_{q}.csv"
        f_share = load(fpath, ascii_only)[2]
        s_share = load(spath, ascii_only)[2]
        f_avoid = [load(f"{src}/first_{a}.csv", ascii_only)[2] for a in avoid]
        s_avoid = [load(f"{src}/sur_{a}.csv", ascii_only)[2] for a in avoid]
        fkeep = lambda n, sh=f_share, av=f_avoid, k=factor: all(not_western(sh, o, k)(n) for o in av)
        skeep = lambda n, sh=s_share, av=s_avoid, k=factor: all(not_western(sh, o, k)(n) for o in av)
        names, _ = first_names(fpath, ascii_only, fkeep)
        write(f"{out}/{prefix}_first_names.txt", [f"{n}|{g}" for n, g in names])
        sur, _ = surnames(spath, ascii_only, skeep)
        sc = weights(spath, ascii_only)
        write(f"{out}/{prefix}_surnames.txt", [weighted(n, n, sc) for n in sur])


HANGUL = re.compile(r"^[\uAC00-\uD7A3]{1,3}$")


def rok_hangul(src, out):
    """South Korean names in Hangul, with their most common romanization.

    From q_first_ROKKO.rq and q_sur_ROKKO.rq: Wikidata name items carried by
    South Korean citizens, by their Korean ("ko") label. Several romanized
    items share one Hangul spelling (Ji-hyeon and Ji-hyun are both 지현), so
    counts are summed per Hangul name and the romanization most people carry
    is kept.
    """
    def group(path, has_type):
        count, gender, rom = defaultdict(int), defaultdict(lambda: defaultdict(int)), defaultdict(lambda: defaultdict(int))
        for r in csv.DictReader(open(path, encoding="utf-8")):
            ko, n = r["ko"].strip(), int(r["n"])
            if not has_type and len(ko) > 1 and ko.endswith("\uC528"):
                ko = ko[:-1]  # 씨 ("clan") on a surname label: 부씨 -> 부
            if not HANGUL.match(ko):
                continue
            count[ko] += n
            if has_type:
                gender[ko][GENDER[r["type"].rsplit("/", 1)[1]]] += n
            if r["rom"] and VALID.match(fold(r["rom"])):
                rom[ko][fold(r["rom"])] += n
        return count, gender, rom

    def best_rom(rom, ko):
        return max(rom[ko].items(), key=lambda t: (t[1], t[0]))[0] if rom[ko] else ""

    count, gender, rom = group(f"{src}/first_ROKKO.csv", True)
    def g_of(ko):
        g = gender[ko]
        m, f = g["M"], g["F"]
        if g["U"] >= max(m, f) or (m and f and min(m, f) / (m + f) >= 0.3):
            return "U"
        return "M" if m >= f else "F"
    ranked = sorted(count, key=lambda k: (-count[k], k))
    picked, seen = [], set()
    for want in ("F", "M"):  # half from names women carry, half from names men carry
        for ko in ranked:
            if len([p for p in picked if p[2] in (want, "U")]) >= TARGET // 2 and want == "F":
                break
            if ko in seen or g_of(ko) not in (want, "U") or not best_rom(rom, ko):
                continue
            seen.add(ko)
            picked.append((ko, best_rom(rom, ko), g_of(ko)))
            if len(picked) >= TARGET:
                break
    write(f"{out}/rok_hangul_first_names.txt", [f"{k}|{r}|{g}" for k, r, g in sorted(picked)])

    count, _, rom = group(f"{src}/sur_ROKKO.csv", False)
    sur = [(ko, best_rom(rom, ko)) for ko in sorted(count) if best_rom(rom, ko)]
    write(f"{out}/rok_hangul_surnames.txt", [f"{k}|{r}|{count[k]}" for k, r in sur])


# Single-country and sub-continental regions added 2026-11-19.
# (file prefix, CSV tag, citizenship QIDs to keep from that CSV or None for
#  all, fold to ASCII, regions whose names to keep out)
# Asian ones fold to ASCII, per the romanization decision in SPEC.md. The
# Western-derived ones keep their diacritics. The African ones keep them too:
# French and Portuguese names are common there (Félix, João).
MORE = [
    ("jpn", "A", {"Q17"}, True, ["W"]),
    ("phl", "A", {"Q928"}, True, []),      # Spanish and English names are common there
    ("vnm", "VNM", None, True, ["W"]),
    ("tha", "THA", None, True, ["W"]),
    ("naf", "NAF", None, False, []),       # Morocco, Algeria, Tunisia, Libya, Egypt, Sudan, Western Sahara
    ("caf", "CAF", None, False, []),       # UN Middle Africa: Angola to Sao Tome
    ("saf", "SAF", None, False, []),       # UN Southern Africa: South Africa, Botswana, Eswatini, Lesotho, Namibia
    ("bra", "W", {"Q155"}, False, []),
    ("gbr", "W", {"Q145"}, False, []),
    ("fra", "W", {"Q142"}, False, []),
    ("esp", "W", {"Q29"}, False, []),
    ("prt", "W", {"Q45"}, False, []),
    ("grc", "W", {"Q41"}, False, []),
    ("nor", "W", {"Q20"}, False, []),
    ("swe", "W", {"Q34"}, False, []),
    ("fin", "W", {"Q33"}, False, []),
]
# Vietnamese names are one syllable each (Minh, Lan, Nguyen). After folding to
# ASCII, a valid syllable is an optional initial, a vowel nucleus and an
# optional final. Foreign-born citizens' names (Jessica, Patrick) fail it.
VI_SYLLABLE = re.compile(
    r"^(ngh|ng|nh|ch|gh|gi|kh|ph|qu|th|tr|b|c|d|g|h|k|l|m|n|p|r|s|t|v|x)?"
    r"(uye|uya|uyu|ieu|yeu|uoi|uou|oai|oay|oeo|uay|uya|ai|ao|au|ay|eo|eu|ia|ie|iu|oa|oe|oi|oo|ua|ue|ui|uo|uu|uy|ye|a|e|i|o|u|y)"
    r"(ch|ng|nh|c|m|n|p|t)?$")
# Names any 10+ people carry in the Western data. For Thailand these are
# foreign-born citizens' names (Stephen, Reese); real Thai names almost never
# reach 10 there.
STRICT = {"vnm": lambda n: bool(VI_SYLLABLE.match(n.lower())), "tha": "W"}

# Greek surnames have masculine and feminine forms (Papadopoulos and
# Papadopoulou, Nikolaidis and Nikolaidi), paired from the data like Slavic ones.
GREEK_FEM_TO_MASC = [(r"ou$", ["os"]), (r"i$", ["is"]), (r"a$", ["as"])]


def more_regions(src, out):
    for prefix, tag, qids, ascii_only, avoid in MORE:
        def cut(kind):
            path = f"{src}/{kind}_{tag}.csv"
            if qids is None:
                return path
            tmp = f"{src}/{kind}_{prefix.upper()}_cut.csv"
            with open(path, encoding="utf-8") as fin, open(tmp, "w", encoding="utf-8", newline="") as fout:
                r = csv.DictReader(fin)
                w = csv.DictWriter(fout, fieldnames=r.fieldnames)
                w.writeheader()
                for row in r:
                    if row["c"].rsplit("/", 1)[1] in qids:
                        w.writerow(row)
            return tmp
        fpath, spath = cut("first"), cut("sur")
        f_share, s_share = load(fpath, ascii_only)[2], load(spath, ascii_only)[2]
        f_av = [load(f"{src}/first_{a}.csv", ascii_only)[2] for a in avoid]
        s_av = [load(f"{src}/sur_{a}.csv", ascii_only)[2] for a in avoid]
        fkeep = lambda n: all(not_western(f_share, o)(n) for o in f_av)
        skeep = lambda n: all(not_western(s_share, o)(n) for o in s_av)
        rule = STRICT.get(prefix)
        if callable(rule):
            fkeep = lambda n, base=fkeep: base(n) and rule(n)
            skeep = lambda n, base=skeep: base(n) and rule(n)
        elif rule:
            def common(kind):
                return {k for r in csv.DictReader(open(f"{src}/{kind}_{rule}.csv", encoding="utf-8"))
                        if int(r["n"]) >= 10 for k in (r["name"].lower(), fold(r["name"]).lower())}
            cf, cs = common("first"), common("sur")
            fkeep = lambda n, base=fkeep: base(n) and n.lower() not in cf
            skeep = lambda n, base=skeep: base(n) and n.lower() not in cs
        names, _ = first_names(fpath, ascii_only, fkeep)
        write(f"{out}/{prefix}_first_names.txt", [f"{n}|{g}" for n, g in names])
        sur, _ = surnames(spath, ascii_only, skeep)
        if prefix == "grc":
            pool = {n for v in load(spath)[0].values() for n in v}
            global FEM_TO_MASC
            saved, FEM_TO_MASC = FEM_TO_MASC, GREEK_FEM_TO_MASC
            try:
                pairs = pair_genders(sur, pool)
            finally:
                FEM_TO_MASC = saved
            gc = weights(spath, ascii_only)
            write(f"{out}/{prefix}_surnames.txt", [weighted(m, f, gc) for m, f in pairs])
        else:
            sc = weights(spath, ascii_only)
            write(f"{out}/{prefix}_surnames.txt", [weighted(n, n, sc) for n in sur])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
