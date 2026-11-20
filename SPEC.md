# faux-person: specification

A Java library that generates random, realistic-looking but fictitious `Person`
objects for tests, demos and seed data.

| | |
|---|---|
| Coordinates | `org.tauasa.apps:faux-person:0.1.0-SNAPSHOT` |
| Base package | `org.tauasa.apps.faux` |
| Java | 17 (`maven.compiler.release=17`) |
| Build | Maven |
| Runtime dependencies | None. JUnit 5 is test-scope only |
| Status | Draft, 2026-11-19 |

## Goals

1. One call gives a complete, internally consistent person: the email matches
   the name, the age matches the date of birth, the first name matches the
   gender.
2. **Reproducible.** The same seed always gives the same sequence of people, on
   any JVM, so a failing test can be replayed.
3. **Never a real person's contact details.** Every email and phone number it
   produces is in a range reserved for fiction or documentation, so generated
   data can't reach, or be mistaken for, someone real.
4. Zero runtime dependencies, so adding it costs a consumer nothing.

## Non-goals for v1

- Locales other than en-US.
- Government IDs, payment card numbers, bank accounts or passwords. These look
  like restricted data, and a library that makes convincing ones mostly helps
  fraud, not testing. They stay out by design, not just for now.
- Publishing to Maven Central. v1 installs locally (`mvn install`) or from a
  GitHub release.
- Thread-safe sharing of one generator (see *Concurrency*).

## 1. Data model

All types are immutable records in `org.tauasa.apps.faux`.

```java
public record Person(
    String firstName,
    String lastName,
    Gender gender,
    LocalDate dateOfBirth,
    String email,
    String phone,
    Address address) {

    /** Whole years on the given date. */
    public int ageOn(LocalDate date) { ... }
}

public record Address(
    String street,      // "1234 Maple Ave"
    String city,        // "Springfield"
    String state,       // two-letter USPS code, "IL"
    String postalCode,  // five digits, "62704"
    String country) {}  // always "US" in v1

public enum Gender { FEMALE, MALE, NONBINARY }
```

The records' compact constructors reject `null` fields, so a `Person` built by
hand is held to the same rules as a generated one.

Age is a method, not a field, because a stored age goes stale. `Person` doesn't
read the clock. The caller passes the date, or the generator's `Clock` supplies
it.

## 2. Generating people

### API

```java
PersonGenerator gen = PersonGenerator.builder()
    .seed(42L)                       // optional; default is a random seed
    .ageRange(18, 90)                // inclusive; default 18..90
    .genderWeights(49, 49, 2)        // FEMALE, MALE, NONBINARY; default shown
    .clock(Clock.systemDefaultZone())// optional; fixes "today" for ages
    .build();

Person p = gen.next();
List<Person> ten = gen.next(10);
Stream<Person> many = gen.stream();  // infinite; caller limits it
long seed = gen.seed();              // the seed in use, to log and replay
```

`PersonGenerator.builder()` validates when `build()` is called: `IllegalArgumentException`
for `min > max`, `min < 0`, `max > 120`, or weights that are negative or sum to
zero.

### How each field is made

The order is fixed, and each field draws from the generator's one random
source. That's what makes a seed reproducible. Changing the order or the number
of draws changes every later person, so it's a breaking change (see *Versioning*).

| Order | Field | Rule |
|---|---|---|
| 1 | `gender` | Weighted pick from `genderWeights` |
| 2 | `firstName` | Uniform pick from the female or male list. `NONBINARY` picks a list at 50/50 first |
| 3 | `lastName` | Uniform pick from the surname list |
| 4 | `dateOfBirth` | Uniform day between the earliest and latest birthdays that give an age in `ageRange` on the clock's today |
| 5 | `email` | `first.last` lowercased, ASCII only, plus a random 0–99 suffix on a coin flip, at a domain picked from `example.com`, `example.net` and `example.org` |
| 6 | `phone` | `(AAA) 555-01NN`: a random area code from the bundled list, then 555-0100 to 555-0199 |
| 7 | `address.state` and `city` | Uniform pick of one row from the bundled city list, which pairs a city with its state |
| 8 | `address.street` | Number 1–9999, then a street name and a suffix (`St`, `Ave`, `Rd`, `Ln`, `Dr`, `Ct`, `Way`, `Pl`) from the bundled lists |
| 9 | `address.postalCode` | Five random digits, `00501` to `99950` |

**Why these ranges.** RFC 2606 reserves `example.com`, `.net` and `.org`: mail
to them never reaches anyone. NANP sets aside 555-0100 to 555-0199 for
fiction. Postal codes are random, not looked up, so they don't need to match
the city. A test that needs a real ZIP for a real city is out of scope.

### The random source

`java.util.SplittableRandom` seeded from the builder. Its algorithm is
specified in the JDK documentation and doesn't change between releases, unlike
`ThreadLocalRandom`, so a seed gives the same people on any JDK 17+.

### Bundled data

Plain text, one entry per line, UTF-8, under
`src/main/resources/org/tauasa/apps/faux/data/`:

| File | Content | Source |
|---|---|---|
| `first-female.txt`, `first-male.txt` | The 500 most common first names of each sex | US Social Security Administration, national baby-name data. A US government work, public domain |
| `surnames.txt` | The 1,000 most common surnames | US Census Bureau, 2010 surname file. A US government work, public domain |
| `cities.txt` | `City,ST` pairs, about 300 | US Census Bureau places list, public domain |
| `streets.txt` | About 200 generic street names (`Maple`, `Oak`, `Main`, `2nd`) | Written for this project |
| `area-codes.txt` | US area codes in service | NANPA public list |

The lists are read once, lazily, into immutable `List<String>`s shared by all
generators. A missing or empty file is an `IllegalStateException` at first
use, naming the file. Each file's header comment names its source and the
date it was taken, and a `NOTICE` file at the root repeats that.

## 3. How a consumer uses it

```xml
<dependency>
  <groupId>org.tauasa.apps</groupId>
  <artifactId>faux-person</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```

```java
var gen = PersonGenerator.builder().seed(7).build();
var alice = gen.next();
assertEquals(7L, gen.seed());
```

A failing test logs `gen.seed()`, and the person re-runs it with that seed to
get the same data.

### Concurrency

A `PersonGenerator` isn't thread-safe. For parallel work, `gen.split()` returns
a new generator with its own `SplittableRandom.split()` stream, the same
settings, and a reproducible sequence of its own. `stream()` is sequential.
Calling `.parallel()` on it is documented as unsupported.

## 4. Tests

JUnit 5, run by `mvn verify`. Each one checks behaviour from the public API.

| Test | Fails when |
|---|---|
| Same seed, same 1,000 people | Generation stops being reproducible |
| A golden file: seed 42's first 5 people, checked into `src/test/resources` | The draw order or the data changes without a version bump |
| Different seeds give different first people | The seed is ignored |
| Every email ends in an RFC 2606 domain, and matches `^[a-z]+\.[a-z]+\d{0,2}@example\.(com|net|org)$` | A real-looking address can come out |
| Every phone matches `^\(\d{3}\) 555-01\d{2}$` | A real number can come out |
| With a fixed `Clock`, every `ageOn(today)` is within `ageRange`, at both edges (`ageRange(30,30)`) | The date-of-birth bounds are off by one |
| A birthday on 29 February, aged on 28 February and 1 March of a non-leap year | Leap-year age is wrong |
| Weights `(1,0,0)` give only `FEMALE`, and first names only from the female list | Weights or the list pick are ignored |
| Invalid builder settings throw `IllegalArgumentException` | Bad config is accepted silently |
| `split()` sequences are reproducible and differ from the parent's | Splitting shares or ignores state |
| Records reject `null` | A half-built `Person` can exist |

## 5. Repo and CI

- GitHub `tauasa/faux-person`, private, `main` protected: pull requests
  required, no force pushes or deletions, admins included.
- `.githooks/pre-commit` copied from Sanvio Factory (the credential scan), with
  `core.hooksPath` set in each clone.
- GitHub Actions: `mvn -B verify` on JDK 17 on every pull request and push to
  `main`. Its check is made required once it has run on a pull request.
- `LICENSE`: MIT, matching the photomosaic project. **To confirm.**

## Versioning

Semantic versioning. Changing the draw order, a draw's range or a bundled list
changes what a seed produces. That's a breaking change for anyone with golden
files, so it needs a minor bump while on 0.x and a major one after 1.0, and a
line in `CHANGELOG.md`.

## Open questions

1. Licence: MIT? (assumed above)
2. Should `NONBINARY` be on by default at 2%, or off (weight 0) unless asked
   for? This spec says on.
