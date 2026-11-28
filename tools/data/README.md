# Rebuilding the bundled data

These scripts built the files in
`src/main/resources/org/tauasa/faux/data/`, which go in the jar, and in
`extra/`, which the generator doesn't read. Each file's first line is a
`#` header with its fields, source and build date. They
need Python 3.10+ and nothing outside the standard library. Rebuilding gives
different files whenever Wikidata or GeoNames has changed, and that changes
what every seed produces (see *Versioning* in [SPEC.md](../../SPEC.md)).

Keep downloads in their own folder, outside the repo, and run each script with
`python3 -I`.

```bash
D=~/faux-data            # downloads and intermediate files
OUT=src/main/resources/org/tauasa/faux/data
EXTRA=tools/data/extra
mkdir -p $D/wd $D/geonames $D/tiger

# 1. Names (Wikidata via QLever). The queries/*.rq files hold each region's
#    country list. wdq.py retries when the endpoint rate-limits (HTTP 429).
#    W, R, A are the western, rus and asia regions. jpn and phl are cut
#    from A, and bra, gbr, fra, esp, prt, grc, nor, swe and fin from W. ISR
#    feeds heb, and ROKKO feeds rok: Hangul labels, each written with its
#    most common romanization and the Hangul as the native field.
for r in W R A MEX IRN ISR PSE ROKKO VNM THA NAF CAF SAF; do
  python3 -I tools/data/wdq.py tools/data/queries/q_first_$r.rq $D/wd/first_$r.csv
  python3 -I tools/data/wdq.py tools/data/queries/q_sur_$r.rq   $D/wd/sur_$r.csv
done
# Native spellings for jpn, rus, grc, heb, pse, naf and irn: each name item's
# label in that language beside its English one, by how many people carry it.
for r in jpn rus grc heb pse naf irn; do
  python3 -I tools/data/wdq.py tools/data/queries/q_native_first_$r.rq $D/wd/native_first_$r.csv
  python3 -I tools/data/wdq.py tools/data/queries/q_native_sur_$r.rq   $D/wd/native_sur_$r.csv
done
python3 -I tools/data/build_names.py $D/wd $OUT
# Or the native spellings alone, on the lists as they are, which keeps every
# seed's draws. 0.4.0's were added this way, to lists built on 2026-11-20:
#   python3 -I tools/data/build_names.py native $D/wd $OUT

# 2. Cities (GeoNames, CC BY 4.0)
curl -sSfL -o $D/geonames/US.zip https://download.geonames.org/export/zip/US.zip
curl -sSfL -o $D/geonames/cities1000.zip https://download.geonames.org/export/dump/cities1000.zip
unzip -o -q $D/geonames/US.zip -d $D/geonames/zip
unzip -o -q $D/geonames/cities1000.zip -d $D/geonames/cities
python3 -I tools/data/build_cities.py $D/geonames $D      # also writes $D/counties.tsv
cp $D/us_cities_states_zip.txt $EXTRA/

# 3. Streets (Census TIGER/Line 2024 ADDRFEAT, about 200 MB of downloads)
python3 -I tools/data/build_streets.py $D/counties.tsv $D/geonames/zip/US.txt $D/tiger $OUT

# 4. Companies (Wikidata)
python3 -I tools/data/wdq.py tools/data/queries/q_companies.rq        $D/wd/companies.csv
python3 -I tools/data/wdq.py tools/data/queries/q_companies_listed.rq $D/wd/companies_listed.csv
python3 -I tools/data/build_companies.py $OUT 1500 $D/geonames/cities/cities1000.txt $D/geonames/zip/US.txt \
  $D/wd/companies.csv $D/wd/companies_listed.csv
# Companies outside the US: 4,000, at most 120 per country. Needs the US file
# first, so no marked domain repeats a US one.
python3 -I tools/data/wdq.py tools/data/queries/q_companies_intl.rq $D/wd/companies_intl.csv
curl -sSfL -o $D/public_suffix_list.dat https://publicsuffix.org/list/public_suffix_list.dat
python3 -I tools/data/build_companies_intl.py $D/wd/companies_intl.csv $D/public_suffix_list.dat $OUT 120 4000
```

The 2026-11-19 build added one step to step 4 by hand: for companies with no
headquarters at all in QLever's copy of Wikidata, it asked the live Wikidata
Query Service for their `P159`, `P131` or `P276` place names and added those
as `hqName`. That placed 58 more companies. Without it, the file has the same
shape with slightly different companies.

Streets outside the US come after the international companies, since there's a
line for each of their countries:

```bash
# 5. Streets outside the US: Wikidata streets (about 100 MB of query results),
#    placed with GeoNames places and postal codes (about 30 MB of downloads).
python3 -I tools/data/wdq.py tools/data/queries/q_streets_intl.rq $D/wd/streets_intl.csv
curl -sSfL -o $D/geonames/postal-all.zip https://download.geonames.org/export/zip/allCountries.zip
unzip -o -q $D/geonames/postal-all.zip -d $D/geonames/postal
python3 -I tools/data/build_streets_intl.py $D/wd/streets_intl.csv $D/geonames/cities/cities1000.txt \
  $D/geonames/postal/allCountries.txt $OUT/intl_companies_domains.txt $OUT 50 20
```

Then run `mvn verify`: `DataSetTest` checks every file still has the sizes and
shapes the library relies on.
