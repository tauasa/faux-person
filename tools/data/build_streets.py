"""Build us_street_names_city_state_zip.txt from Census TIGER/Line 2024 ADDRFEAT.

usage: python3 -I build_streets.py <counties.tsv> <geonames-zip-US.txt> <download-dir> <out-dir>

For each state, the county of its largest city (falling back to the next if
that county's file is over 40 MB): street names that carry a house-number
range and a ZIP, 30 picked at random (fixed seed), each with the city and
state GeoNames gives for that ZIP.
"""
import io
import os
import random
import struct
import sys
import urllib.request
import zipfile

FIPS = dict(AL="01", AK="02", AZ="04", AR="05", CA="06", CO="08", CT="09", DE="10", FL="12", GA="13",
            HI="15", ID="16", IL="17", IN="18", IA="19", KS="20", KY="21", LA="22", ME="23", MD="24",
            MA="25", MI="26", MN="27", MS="28", MO="29", MT="30", NE="31", NV="32", NH="33", NJ="34",
            NM="35", NY="36", NC="37", ND="38", OH="39", OK="40", OR="41", PA="42", RI="44", SC="45",
            SD="46", TN="47", TX="48", UT="49", VT="50", VA="51", WA="53", WV="54", WI="55", WY="56")
PER_STATE = 30
MAX_BYTES = 40_000_000
UA = {"User-Agent": "faux-person-data-builder/0.1"}
rng = random.Random(20261119)


def dbf_rows(data, encoding="utf-8"):
    """Rows of a dBase file. TIGER/Line declares UTF-8 in its .cpg file."""
    n, hlen = struct.unpack("<IH", data[4:10])
    rlen = struct.unpack("<H", data[10:12])[0]
    fields, pos = [], 32
    while data[pos] != 0x0D:
        name = data[pos:pos + 11].split(b"\0")[0].decode()
        fields.append((name, data[pos + 16]))
        pos += 32
    for i in range(n):
        rec = data[hlen + i * rlen: hlen + (i + 1) * rlen]
        if rec[:1] == b"*":
            continue
        out, off = {}, 1
        for name, size in fields:
            out[name] = rec[off:off + size].decode(encoding).strip()
            off += size
        yield out


def fetch(code, dl):
    path = f"{dl}/tl_2024_{code}_addrfeat.zip"
    url = f"https://www2.census.gov/geo/tiger/TIGER2024/ADDRFEAT/tl_2024_{code}_addrfeat.zip"
    if not os.path.exists(path):
        head = urllib.request.urlopen(urllib.request.Request(url, method="HEAD", headers=UA), timeout=60)
        if int(head.headers.get("Content-Length", 0)) > MAX_BYTES:
            return None
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=300) as r:
            open(path, "wb").write(r.read())
    with zipfile.ZipFile(path) as z:
        return z.read(next(n for n in z.namelist() if n.endswith(".dbf")))


def main(counties_tsv, zipfile_path, dl, out):
    city_of = {}
    for line in open(zipfile_path, encoding="utf-8"):
        f = line.split("\t")
        city_of[f[1]] = (f[2], f[4])
    by_state = {}
    for line in open(counties_tsv, encoding="utf-8"):
        st, fips, _, _ = line.rstrip("\n").split("\t")
        if fips:
            by_state.setdefault(st, []).append(fips)

    lines = []
    for st, fips_list in by_state.items():
        for fips in fips_list:
            data = fetch(FIPS[st] + fips, dl)
            if data:
                break
        else:
            print("no county file for", st)
            continue
        seen = {}
        for r in dbf_rows(data):
            name = r.get("FULLNAME", "")
            z = r.get("ZIPL") or r.get("ZIPR")
            has_range = r.get("LFROMHN") or r.get("RFROMHN")
            if not name or not z or not has_range or z not in city_of:
                continue
            if any(ch.isdigit() for ch in name.split()[0]) and name.split()[0].isdigit():
                continue  # "10 Mile Rd" style keep; pure numbers like "101" drop
            if name.upper().startswith(("I- ", "US HWY", "STATE HWY", "CO RD", "CR ", "SR ", "HWY ")):
                continue
            seen.setdefault((name.lower(), z), (name, z))
        picks = rng.sample(sorted(seen.values()), min(PER_STATE, len(seen)))
        for name, z in picks:
            city, zst = city_of[z]
            lines.append(f"{name}|{city}|{zst}|{z}")
    lines.sort(key=lambda l: (l.split("|")[2], l.split("|")[1].lower(), l.lower()))
    with open(f"{out}/us_street_names_city_state_zip.txt", "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")
    print(len(lines), "lines from", len({l.split('|')[2] for l in lines}), "states")


if __name__ == "__main__":
    main(*sys.argv[1:5])
