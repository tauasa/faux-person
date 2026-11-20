# faux-person

A Java 17 library and command-line tool that generate random, realistic people
for tests, demos and seed data. Each person has a name, gender, date of birth,
email, phone, US street address and employer, and the fields agree with each
other: the email is built from the name and the employer, a woman with
a Russian surname gets the feminine form (Petrova, not Petrov), and the age
fits the date of birth. Names come up about as often as they do in real life:
about one South Korean in five is a Kim, and so is about one generated one,
and Aleksandr is the most common name for a Russian man.

Give it a seed and you get the same people every time, so a failing test can
be replayed exactly. Birth dates count back from today, so fix the date too
(`--today` on the command line, `.clock(...)` in Java) when you need the same
birth dates on another day. It has no runtime dependencies.

```text
$ ./faux-person.sh -n 3 --seed 42 --today 2026-11-20
Juha Karvonen (male, 63) <juha.karvonen@morganstanley.example> (319) 555-0128, 9694 W 6th Ave, Humboldt, SD 57035, works at *Morgan Stanley
Jose Barra (male, 36) <jose.barra48@fourwaybooks.example> (260) 555-0158, 8418 Golden Aster Ct, Noblesville, IN 46062, works at *Four Way Books
Jens Klausen (male, 73) <jens.klausen@catabasis.example> (959) 555-0112, 4859 Page St, Manchester, NH 03109, works at *Catabasis Pharmaceuticals
```

> **Emails and phone numbers can't reach anyone.** Employers are real US
> companies, marked so nobody mistakes them for a real person's employer: the
> name starts with `*` and the domain is under `.example`, which is reserved so
> mail to it is never delivered (`*Costco`, `jane.doe@costco.example`). Phone
> numbers are always in the 555-0100 to 555-0199 range, which is reserved for
> fiction. Addresses use real streets with a random house number, so one can
> match a real home: don't mail anything to a generated address.
>
> `--real-companies`, or `.realCompanies(true)` in Java, drops the marks and
> uses the real names and domains (`Costco`, `jane.doe@costco.com`). An email
> like that can be someone's real inbox, so never send to one.

## The model

```mermaid
classDiagram
    class Person {
        String firstName
        String lastName
        Gender gender
        Region region
        LocalDate dateOfBirth
        String email
        String phone
        Address address
        Company employer
        int ageOn(LocalDate date)
    }
    class Address {
        String street
        String city
        String state
        String postalCode
        String country
    }
    class Company {
        String name
        String city
        String state
        String domain
    }
    class Gender {
        <<enumeration>>
        FEMALE
        MALE
        NONBINARY
    }
    class Region {
        <<enumeration>>
        WESTERN
        ASIA
        RUS
        JPN
        GBR
        ... 24 values
    }
    Person --> Address : address
    Person --> Company : employer
    Person --> Gender
    Person --> Region
```

*Figure: a `Person` holds a `Gender`, a `Region`, an `Address` and an employer
`Company`. All five types are in `org.tauasa.faux.model`. `Person`,
`Address` and `Company` are immutable records, and `Gender` and `Region` are
enums. `PersonGenerator`, in `org.tauasa.faux`, creates people.*

| Type | What it holds |
|---|---|
| `Person` | Name, gender, the region the names came from, date of birth, email, phone, address and employer. There's no age field, because a stored age goes stale: call `ageOn(date)` |
| `Address` | Street with house number (`2304 Eileen Ave`), city, two-letter state, five-digit ZIP, and country, always `US` |
| `Company` | A US company: name, headquarters city and state, and web domain, which the email uses. Generated ones are real companies, marked by default (`*Costco`, `costco.example`) |
| `Gender` | `FEMALE`, `MALE` or `NONBINARY`. Women get women's and unisex first names and the feminine surname form, men get men's and unisex names and the masculine form. A nonbinary person gets a woman's or a man's first name at 50/50, and either surname form at 50/50 |
| `Region` | Where the names come from, in 24 values. Three broad ones: `WESTERN` (the Americas, Europe, Australia, New Zealand), `ASIA` (East, South and Southeast Asia) and `RUS` (Russia and the former Soviet Union). Three African: `NAF`, `CAF`, `SAF` (North, Central and Southern Africa). The rest are countries, by ISO code: `ROK` (South Korea), `JPN`, `PHL`, `VNM`, `THA`, `MEX`, `BRA`, `IRN`, `PSE`, `GBR`, `FRA`, `ESP`, `PRT`, `GRC`, `NOR`, `SWE`, `FIN`. The odd one out is `HEB`, which holds Hebrew names, from Israeli citizens |

Every record rejects `null` fields.

## Use it in a Java project

It isn't on Maven Central yet. Install it into your local Maven repository:

```bash
git clone https://github.com/tauasa/faux-person.git
cd faux-person
mvn install
```

Then add it to your project, usually in test scope:

```xml
<dependency>
  <groupId>org.tauasa</groupId>
  <artifactId>faux-person</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```

Generate people:

```java
import java.time.LocalDate;
import java.util.List;
import org.tauasa.faux.PersonGenerator;
import org.tauasa.faux.model.Person;
import org.tauasa.faux.model.Region;

PersonGenerator gen = PersonGenerator.builder()
        .seed(42)                    // optional: the same seed gives the same people
        .region(Region.ASIA)         // optional: default picks a region per person
        .ageRange(25, 40)            // optional: default 18 to 90, inclusive
        .genderWeights(50, 50, 0)    // optional: FEMALE, MALE, NONBINARY (default 49, 49, 2)
        .realCompanies(false)        // optional: true for real employer names and email domains
        .build();

Person p = gen.next();
List<Person> team = gen.next(20);
List<Person> adults = gen.stream().filter(x -> x.ageOn(LocalDate.now()) >= 21).limit(100).toList();
```

To replay a failure, log `gen.seed()` and pass it back to `.seed(...)`. That
works even if you didn't set a seed, because the generator reports the random
one it picked. Pass `.clock(Clock.fixed(...))` too if a test checks ages, so
"today" doesn't move.

A generator isn't thread-safe. Give each thread its own with `gen.split()`,
which keeps the settings and starts a separate sequence you can replay.
Splitting draws from the parent, so it changes the parent's later people too:
to replay a run, call `split()` at the same points.

`build()` throws `IllegalArgumentException` if the age range falls outside
0 to 120 or is reversed, or if the gender weights are negative or all zero.

### Quick lists

For a list in one line, use a static method. It uses the defaults (ages 18 to
90, gender weights 49/49/2, today's date) and throws
`IllegalArgumentException` for a negative count. There's one method for every
region, named for its people, and three general ones:

```java
List<Person> anyone    = PersonGenerator.generateListOfRandomPeople(10);
List<Person> world     = PersonGenerator.generateListOfRandomPeopleFromAroundTheWorld(5, 20); // 5 to 20 people
List<Person> mexicans  = PersonGenerator.generateListOfRandomPeople(10, Region.MEX);
List<Person> americans = PersonGenerator.generateListOfRandomAmericans(10);  // WESTERN names
List<Person> koreans   = PersonGenerator.generateListOfRandomKoreans(10);    // ROK
```

The rest follow the same pattern: `Asians`, `Russians`, `Japanese`, `Filipinos`,
`Vietnamese`, `Thais`, `Mexicans`, `Brazilians`, `Iranians`, `Israelis` (`HEB`),
`Palestinians`, `NorthAfricans`, `CentralAfricans`, `SouthernAfricans`,
`Britons`, `French`, `Spaniards`, `Portuguese`, `Greeks`, `Norwegians`,
`Swedes` and `Finns`. There's no US-only region, so `Americans` draws from
`WESTERN`, which also covers Europe and Australia. Each call picks a new random
seed, so you can't replay its people. Use the builder when you need a seed,
an age range or a fixed date.

### Use your own data

Point the builder at a folder of your own data files. Each file in it replaces
the bundled file with the same name, and every other file still comes from the
bundled data:

```java
import java.nio.file.Path;

PersonGenerator gen = PersonGenerator.builder()
        .dataDirectory(Path.of("my-data"))   // say, my-data/western_surnames.txt
        .build();
```

The names are `<region>_first_names.txt` and `<region>_surnames.txt` for each
region in lowercase (`western`, `rok`, …), plus `us_companies_domains.txt` and
`us_street_names_city_state_zip.txt`. Each file uses the format its bundled
copy names in its first line, such as `# name|gender|weight`. Weights are
optional, and a name without one counts as 1. Lines starting with `#` are
skipped.

`build()` reads the files, so a mistake shows up there. It throws
`IllegalArgumentException` if the folder doesn't exist, or holds a `.txt`
file with a name it doesn't know, which is usually a misspelling that would
otherwise be quietly ignored. It throws `IllegalStateException`, naming the
file and line, for an empty file or a bad line. Each `build()` reads the
folder again, so build once and reuse the generator, or `split()` it. On the
command line, `--data-dir DIR` does the same.

## Use the command line

`faux-person.sh` builds the jar with Maven the first time you run it, and again
whenever `pom.xml` or anything under `src/` is newer than the jar, then runs the
CLI. People go to stdout, or to a file with `-o`. The seed in use
always goes to stderr, so it never ends up in your output.

```bash
./faux-person.sh                                   # 10 people to stdout
./faux-person.sh -n 5 --seed 42 --today 2026-11-20 # the same 5 people, birth dates too, every day
./faux-person.sh -n 1000 -f json -o people.json    # 1,000 people to a file, as JSON
./faux-person.sh -n 200 -r rus -f pipe > rus.txt   # or redirect stdout yourself
./faux-person.sh -n 50 --min-age 65 --max-age 90 | grep ', FL '
```

| Option | Does | Default |
|---|---|---|
| `-o, --output FILE` | Write to `FILE` instead of stdout. A failed run leaves no file behind | stdout |
| `--rebuild` | Rebuild the jar first, even if it looks up to date | |
| `-n, --count N` | How many people | 10 |
| `-s, --seed S` | Seed, to get the same people again | random |
| `-r, --region R` | Any region in lowercase (`western`, `jpn`, `naf`, …), or `mixed`. `--help` lists all 24 | `mixed` |
| `--min-age N`, `--max-age N` | Youngest and oldest age | 18, 90 |
| `--today DATE` | The date ages are counted from, as `YYYY-MM-DD`. With `--seed`, it gives the same birth dates on any day. Another date changes only the birth dates and ages | today |
| `-f, --format F` | `text`, `pipe` or `json` | `text` |
| `--data-dir DIR` | Read data files from `DIR` in place of the bundled ones with the same names, as `dataDirectory` does in Java ([Use your own data](#use-your-own-data)) | the bundled data |
| `--real-companies` | Use real employer names and email domains (`Costco`, `jane.doe@costco.com`), as `realCompanies(true)` does in Java. The people are the same with or without it | names start with `*`, emails under `.example` |
| `-h, --help` | Show the options | |

`-o` and `--rebuild` belong to the script. Every other option is passed to the
CLI. Set `JAVA_OPTS` to give the JVM options. Without the script, run the jar
directly:

```bash
mvn package
java -jar target/faux-person-0.1.0.jar -n 5 -f json
```

`pipe` prints a header row and then 13 `|`-separated columns. This example
came from `--seed 42 --today 2026-11-20`, and the JSON one below from the same
plus `-r asia`:

```text
first|last|gender|region|dob|age|email|phone|street|city|state|zip|employer
Juha|Karvonen|MALE|FIN|1963-08-07|63|juha.karvonen@morganstanley.example|(319) 555-0128|9694 W 6th Ave|Humboldt|SD|57035|*Morgan Stanley
```

`json` prints one array, with each person on its own line (wrapped here to
fit):

```json
[
  {"firstName":"Maya","lastName":"Guo","gender":"FEMALE","region":"ASIA","dateOfBirth":"2007-02-06","age":19,
   "email":"maya.guo56@spglobal.example","phone":"(319) 555-0128",
   "address":{"street":"9694 W 6th Ave","city":"Humboldt","state":"SD","postalCode":"57035","country":"US"},
   "employer":{"name":"*S&P Global","city":"New York City","state":"NY","domain":"spglobal.example"}}
]
```

When an argument is bad, the CLI names the problem, prints the options, and
exits with code 2. That includes a `--data-dir` that isn't a folder or holds a
misspelled file name. When a file in `--data-dir` is empty or has a bad line,
it names the file and line and exits with code 1.

## Where the data comes from

50 bundled files in `src/main/resources/org/tauasa/faux/data/`, all
UTF-8 and NFC-normalized. Each file's first line names its fields, its source
and the date it was built:

| Data | How many | Source |
|---|---|---|
| First names and surnames for each of the 24 regions | Up to 1,200 per file. Shorter where Wikidata has fewer people, or the country has fewer names: Vietnamese (95 first names), South Korean surnames (123), Palestinian (342 and 188) | Wikidata (CC0), ranked by how many people of each citizenship carry them. Each name also carries that count as its weight (`Kim|6766`, `Aleksandr|M|16684`), and names are drawn in proportion to it. Wikidata counts notable people, so the weights lean toward older generations' names |
| US companies with headquarters and domain | 1,500 | Wikidata: by revenue, then NYSE- and Nasdaq-listed companies by employee count. Headquarters placed with GeoNames (CC BY 4.0) |
| US streets with city, state and ZIP | 1,500, 30 per state | US Census TIGER/Line 2024 (public domain), with each ZIP's city and state from GeoNames |

Three more files are in [`tools/data/extra/`](tools/data/extra/), outside the
jar, until the generator reads them: South Korean names in Hangul with their
romanization and weights (1,200 first names, 118 surnames, from Wikidata), and
1,250 US cities with state and ZIP (25 per state, from GeoNames).

`NOTICE` gives the full attribution. [`tools/data/`](tools/data/) has the
scripts that rebuild the files. [SPEC.md](SPEC.md) explains how each field is
generated and lists the data's known gaps:
- The company list doesn't include every Fortune 500 member. Chevron is one
  that's missing.
- Within a region, the first name and surname can come from different
  countries.

## Build and test

```bash
mvn verify
```

This compiles the code and runs the tests. The tests cover:

- reproducibility, age bounds, emails, phones and addresses
- marked employers by default, and `realCompanies` changing only the employer and email
- how region and gender pick names, including the 50/50 split for nonbinary
  people, and that names come up in proportion to their weights
- every data file: its size and shape, its header, and that it's clean UTF-8
  in NFC, plus that the jar holds only the files the generator reads
- the quick-list methods, one for every region
- `dataDirectory` and the CLI, `--data-dir` included

The build also produces the sources and javadoc jars, and fails if anyone adds
a runtime dependency.

## License

MIT License, in [LICENSE](LICENSE). The bundled GeoNames data is CC BY 4.0,
and [NOTICE](NOTICE) gives its attribution. Both files ship inside the jar
under `META-INF/`.

To publish a release to Maven Central, follow [RELEASING.md](RELEASING.md).
