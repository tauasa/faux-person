package org.tauasa.faux;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import org.tauasa.faux.model.Gender;
import org.tauasa.faux.model.Name;
import org.tauasa.faux.model.NativeName;
import org.tauasa.faux.model.Region;

/**
 * Generates random {@link Name}s: a first name and a surname from one region,
 * drawn the way {@link PersonGenerator} draws them, in proportion to how common
 * each name is.
 *
 * <p>Each name is drawn for a gender, picked by weight as for a person, so the
 * first name and the surname's form agree: a Russian woman's name is
 * {@code Anna Petrova}, not {@code Anna Petrov}. Where the region's data has
 * names in their own script, as South Korea's has Hangul, the name carries that
 * form too ({@link Name#nativeName()}). The same seed and settings
 * always give the same names. A generator is not thread-safe: give each thread
 * its own with {@link #split()}.
 *
 * <pre>{@code
 * NameGenerator gen = NameGenerator.builder().seed(42).region(Region.RUS).build();
 * Name n = gen.next();
 * }</pre>
 */
public final class NameGenerator {

    private final long seed;
    private final SplittableRandom random;
    private final int[] genderWeights;
    private final Region region;
    private final DataSet data;

    private NameGenerator(long seed, SplittableRandom random, int[] genderWeights, Region region, DataSet data) {
        this.seed = seed;
        this.random = random;
        this.genderWeights = genderWeights.clone();
        this.region = region;
        this.data = data;
    }

    /**
     * Starts a builder with the defaults: a random seed, gender weights
     * 49/49/2, and names from every region.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The seed this generator started from. Pass it back to
     * {@link Builder#seed(long)} to get the same names again.
     *
     * @return the seed
     */
    public long seed() {
        return seed;
    }

    /**
     * Generates the next name.
     *
     * @return a new name
     */
    public Name next() {
        return draw(data, region, genderWeights, random).name();
    }

    /**
     * Generates the next {@code count} names.
     *
     * @param count how many, zero or more
     * @return a new mutable list of names
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public List<Name> next(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be zero or more: " + count);
        }
        List<Name> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(next());
        }
        return out;
    }

    /**
     * An infinite, sequential stream of names. Limit it, and don't call
     * {@code parallel()} on it: use {@link #split()} for parallel work.
     *
     * @return a stream drawing from this generator
     */
    public Stream<Name> stream() {
        return Stream.generate(this::next);
    }

    /**
     * A new generator with the same settings and its own reproducible
     * sequence, for another thread. Splitting draws from this generator, so it
     * changes the names this one makes afterwards.
     *
     * @return the new generator
     */
    public NameGenerator split() {
        return new NameGenerator(seed, random.split(), genderWeights, region, data);
    }

    /**
     * A list of random names from every region, with the default settings and
     * a random seed.
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of names
     * @throws IllegalArgumentException if {@code howMany} is negative
     */
    public static List<Name> generateListOfRandomNames(int howMany) {
        return generateListOfRandomNames(howMany, null);
    }

    /**
     * A list of random names from one region, with the default settings and a
     * random seed.
     *
     * @param howMany how many, zero or more
     * @param region  a region, or {@code null} for every region
     * @return a new mutable list of names
     * @throws IllegalArgumentException if {@code howMany} is negative
     */
    public static List<Name> generateListOfRandomNames(int howMany, Region region) {
        return builder().region(region).build().next(howMany);
    }

    /** A drawn name with the region and gender it was drawn for. */
    record Drawn(Region region, Gender gender, Name name) {
    }

    /**
     * Draws a region, a gender and a name that fits both. {@link PersonGenerator}
     * calls this too, so a person's first draws stay in the order SPEC.md fixes
     * (steps 1 to 4 of "How each field is made").
     */
    static Drawn draw(DataSet data, Region region, int[] genderWeights, SplittableRandom random) {
        Region r = region != null ? region : Region.values()[random.nextInt(Region.values().length)];
        Gender gender = pickGender(genderWeights, random);
        DataSet.FirstName fn = data.pickFirstName(r, gender, random);
        DataSet.Surname sn = data.pickSurname(r, random);
        // One choice of form, so the native surname matches the Latin one.
        boolean feminine = switch (gender) {
            case FEMALE -> true;
            case MALE -> false;
            case NONBINARY -> random.nextBoolean();
        };
        String last = feminine ? sn.feminine() : sn.masculine();
        String nativeLast = feminine ? sn.nativeFeminine() : sn.nativeName();
        return new Drawn(r, gender, new Name(fn.name(), last, nativeName(r, fn.nativeName(), nativeLast)));
    }

    /** Regions whose names are written surname first with no space in their own script. */
    private static final Set<Region> SURNAME_FIRST = EnumSet.of(Region.ROK, Region.JPN);

    /**
     * The name in its own script when both the first name and the surname have
     * one. No draw, so it doesn't change what any seed produces.
     */
    static Optional<NativeName> nativeName(Region r, String first, String last) {
        if (first == null || last == null) {
            return Optional.empty();
        }
        String full = SURNAME_FIRST.contains(r) ? last + first : first + " " + last;
        return Optional.of(new NativeName(first, last, full));
    }

    private static Gender pickGender(int[] genderWeights, SplittableRandom random) {
        int total = genderWeights[0] + genderWeights[1] + genderWeights[2];
        int n = random.nextInt(total);
        if (n < genderWeights[0]) {
            return Gender.FEMALE;
        }
        return n < genderWeights[0] + genderWeights[1] ? Gender.MALE : Gender.NONBINARY;
    }

    /** Rejects negative weights, all zeros, and a sum too large for an {@code int}. */
    static void checkGenderWeights(int[] genderWeights) {
        for (int w : genderWeights) {
            if (w < 0) {
                throw new IllegalArgumentException("gender weights must not be negative");
            }
        }
        long sum = (long) genderWeights[0] + genderWeights[1] + genderWeights[2];
        if (sum == 0) {
            throw new IllegalArgumentException("gender weights must not all be zero");
        }
        if (sum > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("gender weights are too large");
        }
    }

    /** Settings for a {@link NameGenerator}. */
    public static final class Builder {
        private Long seed;
        private int[] genderWeights = {49, 49, 2};
        private Region region;
        private Path dataDirectory;

        private Builder() {
        }

        /**
         * Fixes the seed, so the sequence of names can be replayed.
         *
         * @param seed any value
         * @return this builder
         */
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Relative weights for the gender each name is drawn for. {@code (1, 0, 0)}
         * gives only women's names, {@code (0, 1, 0)} only men's.
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
         * region at random for each name, with the first name and surname always
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
         * A folder of your own name files, read in place of the bundled ones
         * with the same names, as {@link PersonGenerator.Builder#dataDirectory(Path)}
         * reads them.
         *
         * @param dir a folder, or {@code null} for the bundled data only
         * @return this builder
         */
        public Builder dataDirectory(Path dir) {
            this.dataDirectory = dir;
            return this;
        }

        /**
         * Checks the settings and builds the generator.
         *
         * @return a new generator
         * @throws IllegalArgumentException if the weights are invalid, the data
         *         directory isn't a folder, or it holds a {@code .txt} file whose
         *         name isn't a data file's
         * @throws IllegalStateException    if a data file in the data directory is
         *         empty or has a bad line; the message names the file and line
         */
        public NameGenerator build() {
            checkGenderWeights(genderWeights);
            long s = seed != null ? seed : new SplittableRandom().nextLong();
            DataSet data = dataDirectory == null ? DataSet.get() : DataSet.load(dataDirectory);
            return new NameGenerator(s, new SplittableRandom(s), genderWeights, region, data);
        }
    }
}
