"""Run a SPARQL query against the QLever Wikidata endpoint and save CSV.

usage: python3 -I wdq.py <query-file> <out.csv>
"""
import sys
import urllib.parse
import urllib.request

PREFIXES = """PREFIX wd: <http://www.wikidata.org/entity/>
PREFIX wdt: <http://www.wikidata.org/prop/direct/>
PREFIX p: <http://www.wikidata.org/prop/>
PREFIX ps: <http://www.wikidata.org/prop/statement/>
PREFIX pq: <http://www.wikidata.org/prop/qualifier/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX schema: <http://schema.org/>
"""

query = PREFIXES + open(sys.argv[1], encoding="utf-8").read()
data = urllib.parse.urlencode({"query": query}).encode()
req = urllib.request.Request(
    "https://qlever.dev/api/wikidata",
    data=data,
    headers={"Accept": "text/csv", "User-Agent": "faux-person-data-builder/0.1"},
)
import time
import urllib.error

for wait in (30, 60, 120, 240, None):
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            body = r.read()
        break
    except urllib.error.HTTPError as e:
        if e.code != 429 or wait is None:
            raise
        print("429, retrying in", wait, "s", file=sys.stderr)
        time.sleep(wait)
open(sys.argv[2], "wb").write(body)
print(sys.argv[2], body.count(b"\n"), "lines")
