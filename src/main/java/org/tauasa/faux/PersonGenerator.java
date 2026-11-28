package org.tauasa.faux;

import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import org.tauasa.faux.model.Address;
import org.tauasa.faux.model.Company;
import org.tauasa.faux.model.Gender;
import org.tauasa.faux.model.Name;
import org.tauasa.faux.model.Person;
import org.tauasa.faux.model.Region;

/**
 * Generates random, internally consistent {@link Person}s.
 *
 * <p>The same seed and settings always give the same sequence of people, on
 * any JDK 17 or later, as long as the bundled data is the same. A generator is
 * not thread-safe: give each thread its own with {@link #split()}.
 *
 * <pre>{@code
 * PersonGenerator gen = PersonGenerator.builder().seed(42).region(Region.ASIA).build();
 * Person p = gen.next();
 * }</pre>
 */
public final class PersonGenerator {

    private final long seed;
    private final SplittableRandom random;
    private final int minAge;
    private final int maxAge;
    private final int[] genderWeights;
    private final Region region;
    private final Clock clock;
    private final DataSet data;
    private final boolean realCompanies;

    private PersonGenerator(long seed, SplittableRandom random, int minAge, int maxAge,
                            int[] genderWeights, Region region, Clock clock, DataSet data,
                            boolean realCompanies) {
        this.seed = seed;
        this.random = random;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.genderWeights = genderWeights.clone();
        this.region = region;
        this.clock = clock;
        this.data = data;
        this.realCompanies = realCompanies;
    }

    /**
     * Starts a builder with the defaults: a random seed, ages 18 to 90, gender
     * weights 49/49/2, names from every region, the system clock, and
     * employers marked as not real (see {@link Builder#realCompanies(boolean)}).
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The seed this generator started from. Log it, and pass it back to
     * {@link Builder#seed(long)} to get the same people again.
     *
     * @return the seed
     */
    public long seed() {
        return seed;
    }

    /**
     * Generates the next person.
     *
     * @return a new person
     */
    public Person next() {
        // The order of draws below is part of the contract: changing it changes
        // what every seed produces (SPEC.md, "How each field is made").
        NameGenerator.Drawn drawn = NameGenerator.draw(data, region, genderWeights, random);
        Name name = drawn.name();
        LocalDate dob = pickDateOfBirth();
        Company employer = CompanyGenerator.draw(data.employers(drawn.region()), realCompanies, random);
        String email = emailLocalPart(name.firstName(), name.lastName()) + "@" + employer.domain();
        String phone = String.format(Locale.ROOT, "(%03d) 555-01%02d", pickAreaCode(), random.nextInt(100));
        Address address = addressIn(employer.address());
        return new Person(name, drawn.gender(), drawn.region(), dob, email, phone, address, employer);
    }

    /**
     * Countries that write the house number before the street name
     * ({@code "12 Rue de Rivoli"}). Everywhere else outside the US it comes
     * after ({@code "Hauptstraße 12"}).
     */
    private static final Set<String> NUMBER_FIRST = Set.of("AU", "BD", "BS", "BW", "CA", "FR", "GB", "GH", "HK",
            "IE", "IN", "JM", "KE", "LK", "LU", "MY", "NA", "NG", "NZ", "PH", "PK", "SG", "TT", "TZ", "UG", "ZA",
            "ZM", "ZW");

    /**
     * Where a person lives: in their employer's country. In the US, a US
     * street, drawn as faux-person always has. Elsewhere, one of that country's
     * streets, or only a city and postal code where the data has no streets
     * for it, or the headquarters' city where it has nothing at all.
     *
     * <p>Every address takes two draws, the street and the house number, used
     * or not, so where a person lives never changes who the seed draws next.
     */
    private Address addressIn(Address employer) {
        String country = employer.country();
        if (country.equals("US")) {
            DataSet.Street s = pick(data.streets);
            int houseNumber = 1 + random.nextInt(9999);
            return new Address(houseNumber + " " + s.street(), s.city(), s.state(), s.postalCode(), "US");
        }
        List<DataSet.Street> streets = data.intlStreets(country);
        DataSet.Street s = streets.isEmpty() ? null : streets.get(random.nextInt(streets.size()));
        int houseNumber = 1 + random.nextInt(199);
        if (s == null) {
            random.nextInt(2);  // the street draw there was nothing to draw from
            return new Address("", employer.city(), "", "", country);
        }
        if (s.street().isEmpty()) {
            return new Address("", s.city(), "", s.postalCode(), country);
        }
        String street = NUMBER_FIRST.contains(country) ? houseNumber + " " + s.street() : s.street() + " " + houseNumber;
        return new Address(street, s.city(), "", s.postalCode(), country);
    }

    /**
     * Generates the next {@code count} people.
     *
     * @param count how many, zero or more
     * @return a new mutable list of people
     */
    public List<Person> next(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be zero or more: " + count);
        }
        List<Person> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(next());
        }
        return out;
    }

    /**
     * An infinite, sequential stream of people. Limit it, and don't call
     * {@code parallel()} on it: use {@link #split()} for parallel work.
     *
     * @return a stream drawing from this generator
     */
    public Stream<Person> stream() {
        return Stream.generate(this::next);
    }

    /**
     * A new generator with the same settings and its own reproducible
     * sequence, for another thread.
     *
     * <p>Splitting draws from this generator, so it changes the people this
     * generator makes afterwards. Replaying a seed gives the same people only if
     * {@code split()} is called at the same points.
     *
     * @return the new generator
     */
    public PersonGenerator split() {
        return new PersonGenerator(seed, random.split(), minAge, maxAge, genderWeights, region, clock, data,
                realCompanies);
    }

    // ---- One-line conveniences -------------------------------------------
    // Each call uses a new generator with a random seed and the default
    // settings, so its people can't be replayed. Use builder() for a seed, an
    // age range or a fixed clock.

    /**
     * A list of random people from every region, with the default settings.
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     * @throws IllegalArgumentException if {@code howMany} is negative
     */
    public static List<Person> generateListOfRandomPeople(int howMany) {
        return generateListOfRandomPeople(howMany, null);
    }

    /**
     * A list of random people from around the world, of a random length: a
     * count from {@code minHowMany} to {@code maxHowMany}, inclusive, each
     * person from a region picked at random. Default settings otherwise.
     *
     * @param minHowMany the fewest people, zero or more
     * @param maxHowMany the most people, at least {@code minHowMany}
     * @return a new mutable list of people
     * @throws IllegalArgumentException if {@code minHowMany} is negative or
     *         greater than {@code maxHowMany}
     */
    public static List<Person> generateListOfRandomPeopleFromAroundTheWorld(int minHowMany, int maxHowMany) {
        if (minHowMany < 0 || minHowMany > maxHowMany) {
            throw new IllegalArgumentException(
                    "need 0 <= minHowMany <= maxHowMany: " + minHowMany + ".." + maxHowMany);
        }
        PersonGenerator gen = builder().build();
        // The count comes from a generator of its own, so the people drawn
        // depend only on the seed, not on how many were asked for.
        int howMany = minHowMany + new SplittableRandom(gen.seed()).split()
                .nextInt(maxHowMany - minHowMany + 1);
        return gen.next(howMany);
    }

    /**
     * A list of random people with names from one region, with the default
     * settings.
     *
     * @param howMany how many, zero or more
     * @param region  a region, or {@code null} for every region
     * @return a new mutable list of people
     * @throws IllegalArgumentException if {@code howMany} is negative
     */
    public static List<Person> generateListOfRandomPeople(int howMany, Region region) {
        return builder().region(region).build().next(howMany);
    }

    /**
     * A list of random Americans, with names from the {@link Region#WESTERN} pool (the Americas, Europe, Australia and New Zealand), since there's no US-only region.
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomAmericans(int howMany) {
        return generateListOfRandomPeople(howMany, Region.WESTERN);
    }

    /**
     * A list of random Asians ({@link Region#ASIA}: East, South and Southeast Asia).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomAsians(int howMany) {
        return generateListOfRandomPeople(howMany, Region.ASIA);
    }

    /**
     * A list of random Russians ({@link Region#RUS}: Russia and the former Soviet Union).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomRussians(int howMany) {
        return generateListOfRandomPeople(howMany, Region.RUS);
    }

    /**
     * A list of random South Koreans ({@link Region#ROK}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomKoreans(int howMany) {
        return generateListOfRandomPeople(howMany, Region.ROK);
    }

    /**
     * A list of random Japanese people ({@link Region#JPN}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomJapanese(int howMany) {
        return generateListOfRandomPeople(howMany, Region.JPN);
    }

    /**
     * A list of random Filipinos ({@link Region#PHL}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomFilipinos(int howMany) {
        return generateListOfRandomPeople(howMany, Region.PHL);
    }

    /**
     * A list of random Vietnamese people ({@link Region#VNM}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomVietnamese(int howMany) {
        return generateListOfRandomPeople(howMany, Region.VNM);
    }

    /**
     * A list of random Thais ({@link Region#THA}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomThais(int howMany) {
        return generateListOfRandomPeople(howMany, Region.THA);
    }

    /**
     * A list of random Mexicans ({@link Region#MEX}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomMexicans(int howMany) {
        return generateListOfRandomPeople(howMany, Region.MEX);
    }

    /**
     * A list of random Brazilians ({@link Region#BRA}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomBrazilians(int howMany) {
        return generateListOfRandomPeople(howMany, Region.BRA);
    }

    /**
     * A list of random Iranians ({@link Region#IRN}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomIranians(int howMany) {
        return generateListOfRandomPeople(howMany, Region.IRN);
    }

    /**
     * A list of random Israelis, with Hebrew names ({@link Region#HEB}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomIsraelis(int howMany) {
        return generateListOfRandomPeople(howMany, Region.HEB);
    }

    /**
     * A list of random Palestinians ({@link Region#PSE}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomPalestinians(int howMany) {
        return generateListOfRandomPeople(howMany, Region.PSE);
    }

    /**
     * A list of random North Africans ({@link Region#NAF}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomNorthAfricans(int howMany) {
        return generateListOfRandomPeople(howMany, Region.NAF);
    }

    /**
     * A list of random Central Africans ({@link Region#CAF}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomCentralAfricans(int howMany) {
        return generateListOfRandomPeople(howMany, Region.CAF);
    }

    /**
     * A list of random Southern Africans ({@link Region#SAF}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomSouthernAfricans(int howMany) {
        return generateListOfRandomPeople(howMany, Region.SAF);
    }

    /**
     * A list of random Britons ({@link Region#GBR}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomBritons(int howMany) {
        return generateListOfRandomPeople(howMany, Region.GBR);
    }

    /**
     * A list of random French people ({@link Region#FRA}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomFrench(int howMany) {
        return generateListOfRandomPeople(howMany, Region.FRA);
    }

    /**
     * A list of random Spaniards ({@link Region#ESP}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomSpaniards(int howMany) {
        return generateListOfRandomPeople(howMany, Region.ESP);
    }

    /**
     * A list of random Portuguese people ({@link Region#PRT}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomPortuguese(int howMany) {
        return generateListOfRandomPeople(howMany, Region.PRT);
    }

    /**
     * A list of random Greeks ({@link Region#GRC}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomGreeks(int howMany) {
        return generateListOfRandomPeople(howMany, Region.GRC);
    }

    /**
     * A list of random Norwegians ({@link Region#NOR}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomNorwegians(int howMany) {
        return generateListOfRandomPeople(howMany, Region.NOR);
    }

    /**
     * A list of random Swedes ({@link Region#SWE}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomSwedes(int howMany) {
        return generateListOfRandomPeople(howMany, Region.SWE);
    }

    /**
     * A list of random Finns ({@link Region#FIN}).
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of people
     */
    public static List<Person> generateListOfRandomFinns(int howMany) {
        return generateListOfRandomPeople(howMany, Region.FIN);
    }

    private LocalDate pickDateOfBirth() {
        LocalDate today = LocalDate.now(clock);
        LocalDate latest = today.minusYears(minAge);                 // turns minAge today
        LocalDate earliest = today.minusYears(maxAge + 1L).plusDays(1); // still maxAge today
        long span = latest.toEpochDay() - earliest.toEpochDay();
        return LocalDate.ofEpochDay(earliest.toEpochDay() + random.nextLong(span + 1));
    }

    private int pickAreaCode() {
        // NANP shape: first digit 2-9, and not an N11 service code.
        while (true) {
            int a = 200 + random.nextInt(800);
            if (a % 100 != 11) {
                return a;
            }
        }
    }

    private String emailLocalPart(String first, String last) {
        String local = ascii(first) + "." + ascii(last);
        if (random.nextBoolean()) {
            local += random.nextInt(100);
        }
        return local;
    }

    private static String ascii(String s) {
        String folded = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        String out = folded.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return out.isEmpty() ? "x" : out;
    }

    private <T> T pick(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    /** Settings for a {@link PersonGenerator}. */
    public static final class Builder {
        private Long seed;
        private int minAge = 18;
        private int maxAge = 90;
        private int[] genderWeights = {49, 49, 2};
        private Region region;
        private Clock clock = Clock.systemDefaultZone();
        private Path dataDirectory;
        private boolean realCompanies;

        private Builder() {
        }

        /**
         * Fixes the seed, so the sequence of people can be replayed.
         *
         * @param seed any value
         * @return this builder
         */
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Inclusive age range on the clock's today.
         *
         * @param min youngest age, 0 or more
         * @param max oldest age, at most 120
         * @return this builder
         */
        public Builder ageRange(int min, int max) {
            this.minAge = min;
            this.maxAge = max;
            return this;
        }

        /**
         * Relative weights for each gender.
         *
         * @param female    weight for {@link Gender#FEMALE}
         * @param male      weight for {@link Gender#MALE}
         * @param nonbinary weight for {@link Gender#NONBINARY}
         * @return this builder
         */
        public Builder genderWeights(int female, int male, int nonbinary) {
            this.genderWeights = new int[] {female, male, nonbinary};
            return this;
        }

        /**
         * Draws every name from one region. The default, {@code null}, picks a
         * region at random for each person, with first and last name always
         * from the same one.
         *
         * @param region a region, or {@code null} for all of them
         * @return this builder
         */
        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        /**
         * The clock that says what today is, for ages.
         *
         * @param clock a clock
         * @return this builder
         */
        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /**
         * A folder of your own data files, read in place of the bundled ones
         * with the same names, such as {@code western_first_names.txt} or
         * {@code us_companies_domains.txt}. Files it doesn't hold come from
         * the bundled data. The files are read when {@link #build()} runs, and
         * use the bundled files' formats (README.md, "Use your own data"). An
         * empty {@code intl_companies_domains.txt} leaves the international
         * companies out, so employers come only from your US file.
         *
         * @param dir a folder, or {@code null} for the bundled data only
         * @return this builder
         */
        public Builder dataDirectory(Path dir) {
            this.dataDirectory = dir;
            return this;
        }

        /**
         * Whether employers and emails use real companies. By default
         * ({@code false}) each employer's name ends with {@code " [faux]"} and
         * its domain, and so the email's, is under the reserved {@code .example}
         * domain, which never delivers mail: {@code Costco [faux]},
         * {@code jane.doe@costco.example}. With {@code true} the employer is the
         * real company and the email is at its real domain, such as
         * {@code jane.doe@costco.com}, which could be a real person's inbox.
         * Either way the same seed gives the same people.
         *
         * @param real {@code true} for real company names and email domains
         * @return this builder
         */
        public Builder realCompanies(boolean real) {
            this.realCompanies = real;
            return this;
        }

        /**
         * Checks the settings and builds the generator.
         *
         * @return a new generator
         * @throws IllegalArgumentException if the age range or weights are invalid,
         *         the data directory isn't a folder, or it holds a {@code .txt} file
         *         whose name isn't a data file's
         * @throws IllegalStateException    if a data file in the data directory is
         *         empty or has a bad line; the message names the file and line
         */
        public PersonGenerator build() {
            if (minAge < 0 || maxAge > 120 || minAge > maxAge) {
                throw new IllegalArgumentException(
                        "age range must satisfy 0 <= min <= max <= 120: " + minAge + ".." + maxAge);
            }
            NameGenerator.checkGenderWeights(genderWeights);
            long s = seed != null ? seed : new SplittableRandom().nextLong();
            DataSet data = dataDirectory == null ? DataSet.get() : DataSet.load(dataDirectory);
            return new PersonGenerator(s, new SplittableRandom(s), minAge, maxAge, genderWeights, region, clock, data,
                    realCompanies);
        }
    }
}
