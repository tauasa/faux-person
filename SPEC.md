# faux-person: specification

A Java library and command-line tool that generate random, realistic-looking
but fictitious `Person` objects for tests, demos and seed data, and names and
companies on their own.

| | |
|---|---|
| Coordinates | `org.tauasa:faux-person:0.2.0` |
| Packages | `org.tauasa.faux` (the generator), `.model` (the records), `.cli` (the tool) |
| Java | 17 (`maven.compiler.release=17`) |
| Build | Maven |
| Runtime dependencies | None. JUnit 5 is test-scope only, and the enforcer plugin bans the rest |
| Status | Draft, 2026-11-19 |

## Decisions

Dated rulings by the project owner. Change one only by writing a new dated line.

| Date | Decision |
|---|---|
| 2026-11-25 | For 0.3.0: **names in their own script**. `Name` gains `Optional<NativeName> nativeName()`, and a new **`NativeName`** record holds the first name, surname and full name as that language writes it (`영민`, `김`, `김영민`). `Region` doesn't change. Any region's first-name and surname lines may carry the native form as a fourth field, and a name gets one only when both halves have it. Today only `ROK` does: `rok_first_names.txt` and `rok_surnames.txt` are rebuilt from the Hangul lists, each line the romanization most people carry with its Hangul last, and the two `rok_hangul_*.txt` files leave `tools/data/extra/`. The Hangul lists' surnames drop the same non-Korean names as before. Draws are unchanged in number and order, so for a given seed only South Korean people change, and only their names and emails. Emails still use the romanized name. The CLIs add the native name in brackets in `text`, a last `native` column in `pipe`, and a `nativeName` object in `json` |
| 2026-11-20 | Version **0.2.0**. `Person`'s first and last name become one **`Name`** record (`firstName`, `lastName`), and `Person` keeps `firstName()` and `lastName()` as shortcuts. **`NameGenerator`** and **`CompanyGenerator`** generate names and companies on their own, with the same builder shape as `PersonGenerator`, and `PersonGenerator` draws through them. The draw order is unchanged, so every seed gives the same people as 0.1.0, and the person CLI's output is the same. Two more CLIs, **`FauxNameCli`** and **`FauxCompanyCli`**, run from `faux-name.sh` and `faux-company.sh`. Building a `Person` by hand changes, so it's a minor bump |
| 2026-11-20 | The mark is a **`[faux]` suffix** on the employer's name (`Costco [faux]`), in place of the leading `*` decided earlier today. Everything else in the row below stands |
| 2026-11-20 | By default each employer is **marked**: its name starts with `*` and its domain, and so the email's, is under `.example` (`*Costco`, `jane.doe@costco.example`), in `Person.employer` too. **`Builder.realCompanies(true)`**, or the CLI's **`--real-companies`**, gives the real name and domain instead (`Costco`, `jane.doe@costco.com`). The switch changes only those two fields: the draw order is the same, so a seed gives the same people either way. This refines the ruling below, which left `Company.domain` real |
| 2026-11-20 | Generated emails are at the employer's domain with its suffix replaced by the reserved **`.example`** (RFC 2606): `costco.com` becomes `costco.example`. This replaces the 2026-11-19 real-domain ruling below, so no generated email can deliver to a real inbox before 0.1.0 is published. `Company.domain` stays the real domain. Draw order and every seed's people are unchanged; only the email text differs |
| 2026-11-19 | Generated emails use **real company domains** from `us_companies_domains.txt`, every time, replacing the reserved `example.*` domains. An address like `jane.doe12@costco.com` could belong to a real person and could deliver. Goal 3 is narrowed to match |
| 2026-11-19 | Addresses use **real street names with their real city, state and ZIP**, plus a random house number 1–9999. A generated address can match a real home. It isn't aimed at any one |
| 2026-11-19 | Asian and Russian/former-Soviet names are **written in Latin letters**: Asian names folded to ASCII (Ting, Nguyen), others keeping their diacritics (Jüri, Nováková) |
| 2026-11-19 | Names come from three **regions**, `WESTERN`, `ASIA` and `RUS`. The default picks one at random per person, with the first and last name always from the same region |
| 2026-11-19 | A fourth region, **`ROK`** (South Korea), with `rok_first_names.txt` and `rok_surnames.txt`. Mixed mode now picks from four regions, so every seed gives different people than before |
| 2026-11-19 | Four more regions: **`MEX`** (Mexico), **`IRN`** (Iran), **`HEB`** (Hebrew names, from Israeli citizens) and **`PSE`** (Palestine). Mixed mode picks from all eight. A Chinese region was built and then dropped at the owner's request |
| 2026-11-19 | **`rok_hangul_first_names.txt`** and **`rok_hangul_surnames.txt`** hold South Korean names in Hangul beside their romanization. They're bundled but the generator doesn't read them yet |
| 2026-11-20 | Sixteen more regions: `JPN`, `PHL`, `VNM`, `THA`, `NAF` (North Africa), `CAF` (Central Africa), `SAF` (Southern Africa), `BRA`, `GBR`, `FRA`, `ESP`, `PRT`, `GRC`, `NOR`, `SWE`, `FIN`. Mixed mode picks from all 24 |
| 2026-11-20 | **Surnames are drawn by weight**: each surname file ends every line with how many people on Wikidata, in that region's countries, carry the name. Every seed gives different surnames than before. Still 0.1.0, since nothing was released |
| 2026-11-20 | **First names are drawn by weight too**, the same way, from weighted lists for women (`F` and `U` names), men (`M` and `U`) and anyone. A name now takes one draw instead of redrawing until it fits, so every seed gives different people again. The Hangul first-name file carries weights as well |
| 2026-11-20 | Every data file starts with a **`#` header line** naming its fields, its source and its build date. The build scripts write it |
| 2026-11-20 | The three files the generator doesn't read (`us_cities_states_zip.txt` and the two `rok_hangul_*.txt`) **moved out of the jar** to `tools/data/extra/` |
| 2026-11-20 | **`Builder.dataDirectory(Path)`**: a folder whose files replace the bundled ones of the same name. A `.txt` file there with a name the generator doesn't read is an error, not ignored, so a misspelling can't silently fall back to the bundled data |
| 2026-11-20 | **Non-binary first names are 50/50**: a coin flip picks the women's or the men's list, like the surname. Before, every name was in one list, and men's names outweighed women's. Seeds that produce a non-binary person give different people from that point on |
| 2026-11-20 | The CLI's **`--data-dir DIR`** passes a folder to `dataDirectory`. A bad data file exits 1; a bad argument still exits 2 |
| 2026-11-20 | groupId is **`org.tauasa`**, the verified Central namespace, in place of `org.tauasa.apps`. The Java packages move with it, from `org.tauasa.apps.faux` to **`org.tauasa.faux`**, and the bundled data from `org/tauasa/apps/faux/data/` to `org/tauasa/faux/data/`. Nothing had been published under the old names |
| 2026-11-20 | License: **MIT**. Version **0.1.0**, set up for Maven Central. Uploads stop at "validated", so a person presses Publish |
| 2026-11-20 | **Every data file must be clean Unicode**: strict UTF-8 without a byte-order mark, NFC-normalized, no control or invisible format characters, Unix line endings. `DataSetTest` enforces it |
| 2026-11-19 | Data files keep the names the owner gave them. First placed at the root of `src/main/resources/`, then moved the same day under the package (now **`src/main/resources/org/tauasa/faux/data/`**), so their generic names can't collide with another jar's on a consumer's classpath |

## Goals

1. One call gives a complete, internally consistent person: the email matches
   the name and employer, the age matches the date of birth, the first name
   and surname form match the gender, and both names come from one region.
2. **Reproducible.** The same seed and settings always give the same people,
   on any JDK 17+, for a given release of the bundled data.
3. **Phone numbers are never real.** Every number is in NANP's 555-0100 to
   555-0199 range, set aside for fiction. **By default emails are never
   deliverable**: they're under the reserved `.example` domain, and employer
   names end in `[faux]`. `realCompanies(true)` opts into real names and
   domains, and its emails can be real inboxes. Addresses use real streets by
   decision (above), so nothing should be mailed to one.
4. Zero runtime dependencies.

## Non-goals for v1

- Government IDs, payment card numbers, bank accounts or passwords. They look
  like restricted data, and a library that makes convincing ones mostly helps
  fraud. They stay out by design.
- Locales other than US addresses and phones.
- Matching names to a single country within a region (see *Known limits*).
- Thread-safe sharing of one generator (see *Concurrency*).

## 1. Data model

Immutable records in `org.tauasa.faux.model` (moved there 2026-11-19).
Every compact constructor rejects `null`. The file loader, `DataSet`, stays
package-private in `org.tauasa.faux` and isn't part of the API.

```java
public record Person(Name name, Gender gender, Region region,
                     LocalDate dateOfBirth, String email, String phone,
                     Address address, Company employer) {
    public int ageOn(LocalDate date);   // completed years; date before birth -> IllegalArgumentException
    public String firstName();          // name().firstName()
    public String lastName();           // name().lastName()
}
public record Name(String firstName, String lastName, Optional<NativeName> nativeName) {
    public Name(String firstName, String lastName);   // no native form
    public String fullName();           // "Elena Nikitina"
}
public record NativeName(String firstName, String lastName, String fullName) {}   // 영민, 김, 김영민
public record Address(String street, String city, String state, String postalCode, String country) {}
public record Company(String name, String city, String state, String domain) {}
public enum Gender { FEMALE, MALE, NONBINARY }
public enum Region { WESTERN, ASIA, RUS, ROK, MEX, IRN, HEB, PSE, JPN, PHL, VNM, THA,
                     NAF, CAF, SAF, BRA, GBR, FRA, ESP, PRT, GRC, NOR, SWE, FIN }
```

The constructors check only for `null`. A `Person` built by hand isn't checked
against the generation rules below.

Age is a method, not a field, because a stored age goes stale.

## 2. Generating people

```java
PersonGenerator gen = PersonGenerator.builder()
    .seed(42L)                 // optional; default random
    .ageRange(18, 90)          // inclusive; default 18..90
    .genderWeights(49, 49, 2)  // FEMALE, MALE, NONBINARY; default shown
    .region(Region.ASIA)       // optional; default null = mixed
    .clock(Clock.systemDefaultZone())
    .realCompanies(false)      // default false: "Costco [faux]" at costco.example
    .build();

Person p = gen.next();
List<Person> ten = gen.next(10);
Stream<Person> many = gen.stream();   // infinite, sequential
long seed = gen.seed();               // log it to replay
PersonGenerator other = gen.split();  // for another thread
```

`build()` throws `IllegalArgumentException` unless `0 <= min <= max <= 120`
and the weights are non-negative with a positive sum.

### How each field is made

One `SplittableRandom` per generator, seeded from the builder. Its algorithm is
fixed by the JDK, so a seed means the same draws on any JDK 17+. The draws come
in this order. Changing the order, a range or a bundled file changes what
every seed produces (see *Versioning*).

| # | Field | Rule |
|---|---|---|
| 1 | `region` | The builder's region, or uniform over all 24 when it's `null` |
| 2 | `gender` | Weighted pick from `genderWeights` |
| 3 | `firstName` | From the region's first-name file, each in proportion to its weight, in one draw like the surname: from the `F` and `U` names for women, the `M` and `U` names for men. For non-binary, a coin flip picks the women's or the men's list first, as for the surname, so Wikidata's surplus of men doesn't tilt them toward men's names |
| 4 | `lastName` | From the region's surname file, each in proportion to its weight: one `nextLong(total weight)`, then a binary search of the running totals. Women get the feminine form, men the masculine, non-binary either at 50/50. When the first name's and the surname's lines both carry a native form, the name gets a `NativeName` from them: for `ROK`, surname first with no space (`김영민`), and otherwise first name, space, surname. No draw |
| 5 | `dateOfBirth` | Uniform day between the earliest and latest birthdays that give an age in range on the clock's today |
| 6 | `employer` | Uniform from `us_companies_domains.txt`. Unless `realCompanies` is on, the name gets the suffix ` [faux]` and the domain's suffix is replaced by `.example`. No draw |
| 7 | `email` | `first.last` from the romanized name, folded to lowercase ASCII, plus a 0–99 suffix on a coin flip, `@` the employer's domain (step 6) |
| 8 | `phone` | `(AAA) 555-01NN`: AAA is 200–999 but not N11, NN is 00–99 |
| 9 | `address` | Uniform line from `us_street_names_city_state_zip.txt`, then a house number 1–9999. Country `US` |

Every file is read from the builder's `dataDirectory` when it holds a file of
that name, and from the bundled data otherwise.

### Names and companies on their own

```java
NameGenerator names = NameGenerator.builder()
    .seed(42L).region(Region.RUS).genderWeights(49, 49, 2).dataDirectory(null).build();
Name n = names.next();

CompanyGenerator companies = CompanyGenerator.builder()
    .seed(42L).realCompanies(false).dataDirectory(null).build();
Company c = companies.next();
```

Both have `seed()`, `next()`, `next(int)`, `stream()` and `split()` as above,
plus `NameGenerator.generateListOfRandomNames(n[, region])` and
`CompanyGenerator.generateListOfRandomCompanies(n)`. `NameGenerator` makes
draws 1 to 4 of the table above for each name, and `CompanyGenerator` draw 6
for each company. `PersonGenerator` calls the same code for those steps, so a
seed's first name is its first person's name. A name is drawn for a gender,
but `Name` doesn't hold it. `NameGenerator.build()` rejects the same gender
weights as `PersonGenerator`.

### Concurrency

A generator isn't thread-safe. `split()` returns one with the same settings and
its own reproducible stream. It draws from the parent, so the parent's later
people change too; a replay must split at the same points. Don't call `.parallel()` on `stream()`.

## 3. The bundled data

UTF-8, one entry per line, fields separated by `|`, in `src/main/resources/org/tauasa/faux/data/`.
Blank lines and lines starting with `#` are skipped. Every file's first line is
a `#` header naming its fields, source and build date, for example
`# name|gender|weight; gender is F, M or U (either); weight is how many people
on Wikidata (CC0) carry the name; built 2026-11-20`. A missing or empty file,
or a line with too few fields, fails at first use with `IllegalStateException`
naming the file and line. The jar holds exactly the 50 files the generator
reads; one more is in `tools/data/extra/`, marked *(extra)* below. [`tools/data/`](tools/data/) holds the scripts that built
them, and `NOTICE` gives sources and licenses.

**Name weights.** Every `*_first_names.txt` and `*_surnames.txt` line ends in a
whole number of 1 or more: how many people on Wikidata, in the region's
countries, carry the name. A first-name line is `Aleksandr|M|16684`.
Identical rows from an item's `en` and `mul` labels count once, and a feminine
form's count adds to its masculine form's (Ivanova to Ivanov). So a line is
`Kim|6766`, `Petrov|Petrova|1663` or `Papadopoulos|Papadopoulou|129`. A
surname line
with no weight has weight 1, so an unweighted file is drawn uniformly. The
formats in the table below leave the weight out. For Korea this gives Kim
20%, Lee 15% and Park 7%, against about 21%, 15% and 8% in South Korea. A
first-name line with no weight has weight 1 too.

**Native names.** A first-name or surname line can carry the name in its own
script as a fourth field, after the weight: `Ji-hoon|M|97|지훈`, and
`Kim||6768|김`, where the empty second field means the feminine form is the
same. Three fields still mean `masculine|feminine|weight`. A native field that
is present must not be empty. Only the South Korean files have one today.

| File | Lines | Format | Built from |
|---|---|---|---|
| `western_first_names.txt` | 1,200 | `name\|F/M/U` | Wikidata (CC0): given names of citizens of 56 countries across the Americas, Europe, Australia and New Zealand |
| `western_surnames.txt` | 1,200 | `masculine\|feminine`, or one name when they're the same | Wikidata: family names, same countries |
| `asia_first_names.txt` | 1,200 | `name\|F/M/U` | Wikidata: 20 countries from China, India and Japan to Mongolia and Laos |
| `asia_surnames.txt` | 1,200 | `name` | Wikidata: same countries |
| `rus_first_names.txt` | 1,200 | `name\|F/M/U` | Wikidata: Russia, the USSR, the Russian Empire and the 14 other former Soviet republics |
| `rus_surnames.txt` | 1,200 | `masculine\|feminine` | Wikidata: same |
| `rok_first_names.txt` | 1,200 | `name\|F/M/U\|weight\|native` | Wikidata: the Korean (`ko`) labels of South Korean citizens' given names, each with the romanization most people carry, and how many carry the Hangul name under any romanization (`Ji-hoon\|M\|97\|지훈`). Ji-hyun and Ji-hyeon are both 지현, so they count once, under the more common spelling |
| `rok_surnames.txt` | 111 | `name\|\|weight\|native` | Wikidata: same, for surnames (`Kim\|\|6768\|김`), less the Chinese, Japanese and single-family names `build_names.py` lists. Korea has only about 280 surnames in all. Three romanizations stand for two Hangul names each: `Lee` for 이 and the North Korean spelling 리, and `Song` and `Hong` for the Chinese readings 쑹 and 훙 as well as 송 and 홍 |
| `mex_first_names.txt`, `mex_surnames.txt` | 1,200, 1,200 | `name\|F/M/U`, `name` | Wikidata: Mexican citizens, diacritics kept (Fabián, Mondragón) |
| `irn_first_names.txt`, `irn_surnames.txt` | 539, 844 | same | Wikidata: Iranian citizens, folded to ASCII, less mainly Western names |
| `heb_first_names.txt`, `heb_surnames.txt` | 846, 1,200 | same | Wikidata: Israeli citizens, folded to ASCII. A name stays only if its share of Israeli people is at least its share of Western, of Russian and of Palestinian people. That drops immigrants' names (Barry, Svetlana) and keeps David, Sarah and Michael |
| `pse_first_names.txt`, `pse_surnames.txt` | 342, 188 | same | Wikidata: citizens of the State of Palestine, the Palestinian territories and Mandatory Palestine, and people recorded as ethnically Palestinian, less mainly Western or Israeli names. Wikidata holds few Palestinian people, and few women among them: 96 of the first names fit a woman |

Sixteen more regions follow the same format, `name|F/M/U` for first names and
`name` for surnames. The exception is Greek surnames, which are stored as
`masculine|feminine` (Papadopoulos|Papadopoulou). Counts are first names, then
surnames:

| Region | Counts | Built from Wikidata |
|---|---|---|
| `jpn` | 1,200, 1,200 | Japanese citizens, ASCII, less mainly Western names |
| `phl` | 648, 371 | Filipino citizens, ASCII. Spanish and English names stay, since they're common there |
| `vnm` | 95, 137 | Vietnamese citizens and Vietnamese-language name items, ASCII. Kept only if each is one Vietnamese syllable, which drops foreign-born citizens' names |
| `tha` | 842, 904 | Thai citizens and Thai-language name items, ASCII, less any name 10+ Western people carry |
| `naf` | 1,051, 1,066 | Morocco, Algeria, Tunisia, Libya, Egypt, Sudan, Western Sahara |
| `caf` | 1,005, 568 | Angola, Cameroon, Central African Republic, Chad, both Congos, Equatorial Guinea, Gabon, Sao Tome and Principe |
| `saf` | 1,200, 1,200 | South Africa, Botswana, Eswatini, Lesotho, Namibia. English and Afrikaans names are a large share, as they are there |
| `bra`, `gbr`, `fra`, `esp`, `nor`, `swe`, `fin` | 1,200 each | That country's citizens, diacritics kept |
| `prt` | 598, 605 | Portuguese citizens |
| `grc` | 555, 398 | Greek citizens, in Latin letters |

The African and European lists keep their diacritics (Félix, João, Härkönen). The Asian lists are folded to ASCII.
| `us_companies_domains.txt` | 1,500 | `name\|HQ city\|state\|domain` | Wikidata: US businesses ranked by USD revenue, then NYSE- and Nasdaq-listed ones by employees |
| `us_cities_states_zip.txt` *(extra)* | 1,250 | `city\|state\|zip` | GeoNames (CC BY 4.0): 25 per state, the 10 largest plus 15 at random |
| `us_street_names_city_state_zip.txt` | 1,500 | `street\|city\|state\|zip` | Census TIGER/Line 2024 address features (public domain): 30 per state from its largest city's county |

How names were chosen: each country's names are ranked by how many people of
that citizenship carry them on Wikidata, and countries take turns, rank by
rank, so small countries contribute too. Russia, the USSR, China, India, the
US, the UK, Mexico and Brazil take two or three turns per round. Half of each
first-name list comes from names women carry and half from names men carry. A
name joins the Asian or Russian list only if its share of that region's people
is at least half its share of the Western region's. That keeps out Joseph and
Andrew without dropping Wang, Patel or Anna.

### Known limits of this data

- **Not all of the Fortune 500.** Fortune's list is proprietary, so the
  company file ranks by revenue as Wikidata records it. Some members are
  missing: Chevron, TD Synnex, Ingram Micro and StoneX have no usable
  headquarters or no revenue on Wikidata, and HP Inc. appears under the older
  Hewlett-Packard entry.
- **The Vietnamese lists are small** (95 first names). Vietnamese names are drawn from a small set of syllables, and few Vietnamese people on Wikidata have their names recorded as separate items.
- **Iranian and Palestinian lists are short** (539 and 342 first names), and women's names are a minority in both, because Wikidata has fewer people from those countries.
- **Names mix countries within a region.** A Vietnamese first name can come
  with an Indian surname. A country column would fix it.
- **Wikidata counts notable people, not populations.** The ranking and the
  weights favour names common among people with Wikipedia articles, and the
  gender columns have some noise. For a few countries the weights run high:
  Nguyen is 46% of Vietnamese surnames here, against about 38% in Vietnam.
  First-name weights lean toward older generations, since most notable people
  were born decades ago: John and Charles lead the Western names, not Liam or
  Olivia.
- **Lithuanian and Latvian feminine surnames** aren't derived. The feminine
  form equals the masculine.

## 4. The command-line tool

`java -jar faux-person-<version>.jar [options]` prints people to standard
output. The jar's `Main-Class` is `org.tauasa.faux.cli.FauxPersonCli`.

| Option | Default |
|---|---|
| `-n, --count N` | 10 |
| `-s, --seed S` | random; the seed used goes to standard error |
| `-r, --region R` | `mixed`; R is any region name in lowercase |
| `--min-age N`, `--max-age N` | 18, 90 |
| `--today YYYY-MM-DD` | today; the day birth dates and ages are counted from, as a fixed clock |
| `-f, --format text\|pipe\|json` | text |
| `--data-dir DIR` | the bundled data; files in DIR replace bundled ones of the same name |
| `--real-companies` | off: employer names end in `[faux]`, emails are under `.example` |
| `-h, --help` | |

`pipe` prints a header and 14 columns, the last, `native`, empty unless the
name has a native form. `json` prints one array, with a `nativeName` object
(`firstName`, `lastName`, `fullName`) after `lastName` only when there is
one. `text` puts the native full name first in the brackets:
`Yeong-min Kim (김영민, female, 19) <...>`. A bad argument
prints the problem and the usage to standard error and exits with 2.

Two more CLIs are in the same jar, run with `java -cp`. Both take `-n`, `-s`,
`-f`, `--data-dir` and `-h` as above, print the seed to standard error, and
exit the same way:

| Class | Script | Also takes | `pipe` columns |
|---|---|---|---|
| `org.tauasa.faux.cli.FauxNameCli` | `faux-name.sh` | `-r, --region R` | `first\|last\|native`; `text` adds the native name in brackets |
| `org.tauasa.faux.cli.FauxCompanyCli` | `faux-company.sh` | `--real-companies` | `name\|city\|state\|domain` |

The three scripts share `faux-common.sh`, which builds the jar when the source
is newer and handles `-o FILE` and `--rebuild`.

## 5. Tests

`mvn verify` runs them. Each checks behaviour from the public API, or checks
the bundled files' shape.

| Area | Fails when |
|---|---|
| Same seed, same 1,000 people; different seeds differ; `seed()` replays | Generation stops being reproducible |
| Ages at `ageRange(30,30)` and `(0,120)` with a fixed clock; 29 February | Date-of-birth bounds are off by one |
| Email is `first.last[nn]@` the employer's domain; by default every employer is a company from the file with a `[faux]` name and a `.example` domain, and no two share one; with `realCompanies` it's the company as filed; the switch changes nothing else, `split()` keeps it | Email and employer drift apart, a default email could deliver, or the switch changes who is generated |
| Phone matches `^\([2-9]\d{2}\) 555-01\d{2}$`, no N11 | A real-range number can come out |
| Address is a bundled street line plus a 1–9999 house number | Addresses are invented or mismatched |
| Per region: names come from that region's files and fit the gender; women get feminine surnames | Region or gender rules break |
| Weights `(1,0,0)` and `(0,0,1)`; mixed covers all regions | Weights are ignored |
| The jar holds exactly the files the generator reads; every data file starts with a header | A file is left out, or an unused one creeps back in |
| `dataDirectory` replaces only the files it holds, survives `split()`, and rejects a missing folder, a misspelled file and a bad line | Your own data is ignored or half-read |
| Kim's share of 20,000 South Korean surnames, and Aleksandr's of 20,000 Russian men's first names, within 5 SD of their weights; every name file is weighted | Name weights are ignored or lost |
| `split()` is reproducible and differs from the parent | Splitting shares state |
| Every null field of every record is rejected | A half-built record can exist |
| Each region has 1,000+ distinct first names and surnames, 400+ each for women and men; 20+ cities in each of the 50 states; streets and companies are in the 50 states; 1,000+ companies with distinct domains | A data rebuild shrinks or breaks a file |
| CLI: count, replay by seed, `--today` fixes birth dates and ages (another day changes only those two columns), pipe columns, JSON shape and escaping, bad arguments exit 2, `--data-dir` replaces files and a bad data file exits 1, `--real-companies` drops the marks | The tool's contract breaks |
| Non-binary people's first names split about 50/50 between women's and men's names | Non-binary names lean toward one gender again |
| `NameGenerator`: replay by seed, names from the chosen region, `(1,0,0)` gives women's names and feminine surnames, `split()`, `dataDirectory`, and a seed's first name equals its first person's | Names drift from how people are drawn |
| Native names: every South Korean line carries one Hangul name, no other region's does; a Korean name's Hangul comes from its own lines, surname first; in mixed people only South Koreans have one, and their emails stay ASCII; a plain surname file gives none; the fourth field parses and an empty one is rejected; each CLI format shows it | A name loses or mismatches its script, or an email picks up Hangul |
| `CompanyGenerator`: replay by seed, marked by default, `realCompanies` changes only the marks, `split()`, `dataDirectory` | A default company could deliver mail, or the switch changes which company is drawn |
| Name and company CLIs: formats, replay, `--data-dir`, bad arguments exit 2, a bad data file exits 1, `--real-companies` | Their contract breaks |

## 6. Repo and CI

- GitHub `tauasa/faux-person`, public. `main` isn't protected.
- `.githooks/pre-commit` from Sanvio Factory, with `core.hooksPath` set in each clone.
- GitHub Actions runs `mvn -B verify` on JDK 17 for pull requests and pushes to `main`.
- `LICENSE`: MIT (decided 2026-11-20). `LICENSE` and `NOTICE` ship in the jar under `META-INF/`.
- Maven Central: `mvn -Prelease deploy` signs and uploads, and stops before publishing. [RELEASING.md](RELEASING.md) has the steps.

## Versioning

Semantic versioning. A change to the draw order, a range or a bundled file
changes what a seed produces. That breaks golden files downstream, so it's a
minor bump on 0.x and a major one after 1.0, with a line in `CHANGELOG.md`.
