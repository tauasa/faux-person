package org.tauasa.apps.faux;

import org.tauasa.apps.faux.model.Address;
import org.tauasa.apps.faux.model.Company;
import org.tauasa.apps.faux.model.Gender;
import org.tauasa.apps.faux.model.Person;
import org.tauasa.apps.faux.model.Region;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.stream.Stream;

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

    private PersonGenerator(long seed, SplittableRandom random, int minAge, int maxAge,
                            int[] genderWeights, Region region, Clock clock) {
        this.seed = seed;
        this.random = random;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.genderWeights = genderWeights.clone();
        this.region = region;
        this.clock = clock;
        this.data = DataSet.get();
    }

    /**
     * Starts a builder with the defaults: a random seed, ages 18 to 90, gender
     * weights 49/49/2, names from every region, and the system clock.
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
        Region r = region != null ? region : Region.values()[random.nextInt(Region.values().length)];
        Gender gender = pickGender();
        String first = pickFirstName(r, gender);
        DataSet.Surname sn = pick(data.surnames.get(r));
        String last = switch (gender) {
            case FEMALE -> sn.feminine();
            case MALE -> sn.masculine();
            case NONBINARY -> random.nextBoolean() ? sn.feminine() : sn.masculine();
        };
        LocalDate dob = pickDateOfBirth();
        Company employer = pick(data.companies);
        String email = emailLocalPart(first, last) + "@" + employer.domain();
        String phone = String.format(Locale.ROOT, "(%03d) 555-01%02d", pickAreaCode(), random.nextInt(100));
        DataSet.Street s = pick(data.streets);
        int houseNumber = 1 + random.nextInt(9999);
        Address address = new Address(houseNumber + " " + s.street(), s.city(), s.state(), s.postalCode(), "US");
        return new Person(first, last, gender, r, dob, email, phone, address, employer);
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
        return new PersonGenerator(seed, random.split(), minAge, maxAge, genderWeights, region, clock);
    }

    private Gender pickGender() {
        int total = genderWeights[0] + genderWeights[1] + genderWeights[2];
        int n = random.nextInt(total);
        if (n < genderWeights[0]) {
            return Gender.FEMALE;
        }
        return n < genderWeights[0] + genderWeights[1] ? Gender.MALE : Gender.NONBINARY;
    }

    private String pickFirstName(Region r, Gender gender) {
        List<DataSet.FirstName> all = data.firstNames.get(r);
        if (gender == Gender.NONBINARY) {
            return pick(all).name();  // any name, whichever gender it's usually given to
        }
        char want = gender == Gender.FEMALE ? 'F' : 'M';
        // Redraw until the name fits: F or U for women, M or U for men. Every
        // bundled list is about half each, so this takes two draws on average.
        // The cap stops a hand-edited list with none of one gender from hanging.
        for (int tries = 0; tries < 1000; tries++) {
            DataSet.FirstName f = pick(all);
            if (f.gender() == want || f.gender() == 'U') {
                return f.name();
            }
        }
        throw new IllegalStateException("no " + gender + " first names in the " + r + " list");
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
         * Checks the settings and builds the generator.
         *
         * @return a new generator
         * @throws IllegalArgumentException if the age range or weights are invalid
         */
        public PersonGenerator build() {
            if (minAge < 0 || maxAge > 120 || minAge > maxAge) {
                throw new IllegalArgumentException(
                        "age range must satisfy 0 <= min <= max <= 120: " + minAge + ".." + maxAge);
            }
            for (int w : genderWeights) {
                if (w < 0) {
                    throw new IllegalArgumentException("gender weights must not be negative");
                }
            }
            if ((long) genderWeights[0] + genderWeights[1] + genderWeights[2] == 0) {
                throw new IllegalArgumentException("gender weights must not all be zero");
            }
            if ((long) genderWeights[0] + genderWeights[1] + genderWeights[2] > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("gender weights are too large");
            }
            long s = seed != null ? seed : new SplittableRandom().nextLong();
            return new PersonGenerator(s, new SplittableRandom(s), minAge, maxAge, genderWeights, region, clock);
        }
    }
}
