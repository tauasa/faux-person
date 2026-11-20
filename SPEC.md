# faux-person: specification

A Java library and command-line tool that generate random, realistic-looking
but fictitious `Person` objects for tests, demos and seed data.

| | |
|---|---|
| Coordinates | `org.tauasa.apps:faux-person:0.1.0` |
| Packages | `org.tauasa.apps.faux` (the generator), `.model` (the records), `.cli` (the tool) |
| Java | 17 (`maven.compiler.release=17`) |
| Build | Maven |
| Runtime dependencies | None. JUnit 5 is test-scope only, and the enforcer plugin bans the rest |
| Status | Draft, 2026-11-19 |

## Decisions

Dated rulings by the project owner. Change one only by writing a new dated line.

| Date | Decision |
|---|---|
| 2026-11-19 | Generated emails use **real company domains** from `us_companies_domains.txt`, every time, replacing the reserved `example.*` domains. An address like `jane.doe12@costco.com` could belong to a real person and could deliver. Goal 3 is narrowed to match |
| 2026-11-19 | Addresses use **real street names with their real city, state and ZIP**, plus a random house number 1–9999. A generated address can match a real home. It isn't aimed at any one |
| 2026-11-19 | Asian and Russian/former-Soviet names are **written in Latin letters**: Asian names folded to ASCII (Ting, Nguyen), others keeping their diacritics (Jüri, Nováková) |
| 2026-11-19 | Names come from three **regions**, `WESTERN`, `ASIA` and `RUS`. The default picks one at random per person, with the first and last name always from the same region |
| 2026-11-19 | A fourth region, **`ROK`** (South Korea), with `rok_first_names.txt` and `rok_surnames.txt`. Mixed mode now picks from four regions, so every seed gives different people than before |
| 2026-11-19 | Four more regions: **`MEX`** (Mexico), **`IRN`** (Iran), **`HEB`** (Hebrew names, from Israeli citizens) and **`PSE`** (Palestine). Mixed mode picks from all eight. A Chinese region was built and then dropped at the owner's request |
| 2026-11-19 | **`rok_hangul_first_names.txt`** and **`rok_hangul_surnames.txt`** hold South Korean names in Hangul beside their romanization. They're bundled but the generator doesn't read them yet |
| 2026-11-20 | Sixteen more regions: `JPN`, `PHL`, `VNM`, `THA`, `NAF` (North Africa), `CAF` (Central Africa), `SAF` (Southern Africa), `BRA`, `GBR`, `FRA`, `ESP`, `PRT`, `GRC`, `NOR`, `SWE`, `FIN`. Mixed mode picks from all 24 |
| 2026-11-20 | License: **MIT**. Version **0.1.0**, set up for Maven Central. Uploads stop at "validated", so a person presses Publish |
| 2026-11-20 | **Every data file must be clean Unicode**: strict UTF-8 without a byte-order mark, NFC-normalized, no control or invisible format characters, Unix line endings. `DataSetTest` enforces it |
| 2026-11-19 | Data files keep the names the owner gave them. First placed at the root of `src/main/resources/`, then moved the same day to **`src/main/resources/org/tauasa/apps/faux/data/`**, so their generic names can't collide with another jar's on a consumer's classpath |

## Goals

1. One call gives a complete, internally consistent person: the email matches
   the name and employer, the age matches the date of birth, the first name
   and surname form match the gender, and both names come from one region.
2. **Reproducible.** The same seed and settings always give the same people,
   on any JDK 17+, for a given release of the bundled data.
3. **Phone numbers are never real.** Every number is in NANP's 555-0100 to
   555-0199 range, set aside for fiction. Emails and addresses are realistic
   by decision (above), so generated people must not be used to send anything.
4. Zero runtime dependencies.

## Non-goals for v1

- Government IDs, payment card numbers, bank accounts or passwords. They look
  like restricted data, and a library that makes convincing ones mostly helps
  fraud. They stay out by design.
- Locales other than US addresses and phones.
- Matching names to a single country within a region (see *Known limits*).
- Publishing to Maven Central.
- Thread-safe sharing of one generator (see *Concurrency*).

## 1. Data model

Immutable records in `org.tauasa.apps.faux.model` (moved there 2026-11-19).
Every compact constructor rejects `null`. The file loader, `DataSet`, stays
package-private in `org.tauasa.apps.faux` and isn't part of the API.

```java
public record Person(String firstName, String lastName, Gender gender, Region region,
                     LocalDate dateOfBirth, String email, String phone,
                     Address address, Company employer) {
    public int ageOn(LocalDate date);   // completed years; date before birth -> IllegalArgumentException
}
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
| 3 | `firstName` | Uniform from the region's first-name file, redrawn until it fits: `F` or `U` names for women, `M` or `U` for men, any name for non-binary |
| 4 | `lastName` | Uniform from the region's surname file. Women get the feminine form, men the masculine, non-binary either at 50/50 |
| 5 | `dateOfBirth` | Uniform day between the earliest and latest birthdays that give an age in range on the clock's today |
| 6 | `employer` | Uniform from `us_companies_domains.txt` |
| 7 | `email` | `first.last`, folded to lowercase ASCII, plus a 0–99 suffix on a coin flip, `@` the employer's domain |
| 8 | `phone` | `(AAA) 555-01NN`: AAA is 200–999 but not N11, NN is 00–99 |
| 9 | `address` | Uniform line from `us_street_names_city_state_zip.txt`, then a house number 1–9999. Country `US` |

`us_cities_states_zip.txt` is bundled and loaded but not drawn from in v1.

### Concurrency

A generator isn't thread-safe. `split()` returns one with the same settings and
its own reproducible stream. It draws from the parent, so the parent's later
people change too; a replay must split at the same points. Don't call `.parallel()` on `stream()`.

## 3. The bundled data

UTF-8, one entry per line, fields separated by `|`, in `src/main/resources/org/tauasa/apps/faux/data/`.
Blank lines and lines starting with `#` are skipped. A missing or empty file,
or a line with too few fields, fails at first use with `IllegalStateException`
naming the file and line. [`tools/data/`](tools/data/) holds the scripts that built
them, and `NOTICE` gives sources and licenses.

| File | Lines | Format | Built from |
|---|---|---|---|
| `western_first_names.txt` | 1,200 | `name\|F/M/U` | Wikidata (CC0): given names of citizens of 56 countries across the Americas, Europe, Australia and New Zealand |
| `western_surnames.txt` | 1,200 | `masculine\|feminine`, or one name when they're the same | Wikidata: family names, same countries |
| `asia_first_names.txt` | 1,200 | `name\|F/M/U` | Wikidata: 20 countries from China, India and Japan to Mongolia and Laos |
| `asia_surnames.txt` | 1,200 | `name` | Wikidata: same countries |
| `rus_first_names.txt` | 1,200 | `name\|F/M/U` | Wikidata: Russia, the USSR, the Russian Empire and the 14 other former Soviet republics |
| `rus_surnames.txt` | 1,200 | `masculine\|feminine` | Wikidata: same |
| `rok_first_names.txt` | 1,200 | `name\|F/M/U` | Wikidata: South Korean citizens, romanized and folded to ASCII (Ji-hoon, Su-jin) |
| `rok_surnames.txt` | 123 | `name` | Wikidata: every surname carried by 3+ South Korean citizens, less the Chinese pinyin, Japanese and single-family names `build_names.py` lists. Korea has only about 280 surnames in all |
| `mex_first_names.txt`, `mex_surnames.txt` | 1,200, 1,200 | `name\|F/M/U`, `name` | Wikidata: Mexican citizens, diacritics kept (Fabián, Mondragón) |
| `irn_first_names.txt`, `irn_surnames.txt` | 539, 844 | same | Wikidata: Iranian citizens, folded to ASCII, less mainly Western names |
| `heb_first_names.txt`, `heb_surnames.txt` | 846, 1,200 | same | Wikidata: Israeli citizens, folded to ASCII. A name stays only if its share of Israeli people is at least its share of Western, of Russian and of Palestinian people. That drops immigrants' names (Barry, Svetlana) and keeps David, Sarah and Michael |
| `pse_first_names.txt`, `pse_surnames.txt` | 342, 188 | same | Wikidata: citizens of the State of Palestine, the Palestinian territories and Mandatory Palestine, and people recorded as ethnically Palestinian, less mainly Western or Israeli names. Wikidata holds few Palestinian people, and few women among them: 96 of the first names fit a woman |
| `rok_hangul_first_names.txt` | 1,200 | `hangul\|romanized\|F/M/U` | Wikidata: the Korean (`ko`) labels of South Korean given names, with the romanization most people carry (지훈\|Ji-hoon\|M) |
| `rok_hangul_surnames.txt` | 118 | `hangul\|romanized` | Wikidata: same, for surnames (김\|Kim) |

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
| `us_cities_states_zip.txt` | 1,250 | `city\|state\|zip` | GeoNames (CC BY 4.0): 25 per state, the 10 largest plus 15 at random |
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
- **Wikidata counts notable people, not populations.** The ranking favours
  names common among people with Wikipedia articles, and the gender columns
  have some noise.
- **Lithuanian and Latvian feminine surnames** aren't derived. The feminine
  form equals the masculine.

## 4. The command-line tool

`java -jar faux-person-<version>.jar [options]` prints people to standard
output. The jar's `Main-Class` is `org.tauasa.apps.faux.cli.FauxPersonCli`.

| Option | Default |
|---|---|
| `-n, --count N` | 10 |
| `-s, --seed S` | random; the seed used goes to standard error |
| `-r, --region R` | `mixed`; R is any region name in lowercase |
| `--min-age N`, `--max-age N` | 18, 90 |
| `--today YYYY-MM-DD` | today; the day birth dates and ages are counted from, as a fixed clock |
| `-f, --format text\|pipe\|json` | text |
| `-h, --help` | |

`pipe` prints a header and 13 columns. `json` prints one array. A bad argument
prints the problem and the usage to standard error and exits with 2.

## 5. Tests

`mvn verify` runs them. Each checks behaviour from the public API, or checks
the bundled files' shape.

| Area | Fails when |
|---|---|
| Same seed, same 1,000 people; different seeds differ; `seed()` replays | Generation stops being reproducible |
| Ages at `ageRange(30,30)` and `(0,120)` with a fixed clock; 29 February | Date-of-birth bounds are off by one |
| Email is `first.last[nn]@` the employer's domain, from the company file | Email and employer drift apart |
| Phone matches `^\([2-9]\d{2}\) 555-01\d{2}$`, no N11 | A real-range number can come out |
| Address is a bundled street line plus a 1–9999 house number | Addresses are invented or mismatched |
| Per region: names come from that region's files and fit the gender; women get feminine surnames | Region or gender rules break |
| Weights `(1,0,0)` and `(0,0,1)`; mixed covers all regions | Weights are ignored |
| `split()` is reproducible and differs from the parent | Splitting shares state |
| Every null field of every record is rejected | A half-built record can exist |
| Each region has 1,000+ distinct first names and surnames, 400+ each for women and men; 20+ cities in each of the 50 states; streets and companies are in the 50 states; 1,000+ companies with distinct domains | A data rebuild shrinks or breaks a file |
| CLI: count, replay by seed, `--today` fixes birth dates and ages (another day changes only those two columns), pipe columns, JSON shape and escaping, bad arguments exit 2 | The tool's contract breaks |

## 6. Repo and CI

- GitHub `tauasa/faux-person`, private. `main` isn't protected: GitHub needs a
  paid plan for that on a private repo.
- `.githooks/pre-commit` from Sanvio Factory, with `core.hooksPath` set in each clone.
- GitHub Actions runs `mvn -B verify` on JDK 17 for pull requests and pushes to `main`.
- `LICENSE`: MIT (decided 2026-11-20). `LICENSE` and `NOTICE` ship in the jar under `META-INF/`.
- Maven Central: `mvn -Prelease deploy` signs and uploads, and stops before publishing. [RELEASING.md](RELEASING.md) has the steps.

## Versioning

Semantic versioning. A change to the draw order, a range or a bundled file
changes what a seed produces. That breaks golden files downstream, so it's a
minor bump on 0.x and a major one after 1.0, with a line in `CHANGELOG.md`.
