package org.tauasa.faux;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import org.tauasa.faux.model.Company;

/**
 * Generates random {@link Company}s from the bundled companies: about 1,500 in
 * the US and 4,000 in other countries, drawn alike.
 *
 * <p>By default each company is marked, as {@link PersonGenerator} marks an
 * employer: its name ends with {@code " [faux]"} and its domain is under the
 * reserved {@code .example} ({@code Costco [faux]}, {@code costco.example}).
 * {@link Builder#realCompanies(boolean)} gives the company as it is. Each
 * company is drawn uniformly, so the same one can come up more than once. The
 * same seed and settings always give the same companies. A generator is not
 * thread-safe: give each thread its own with {@link #split()}.
 *
 * <pre>{@code
 * CompanyGenerator gen = CompanyGenerator.builder().seed(42).build();
 * Company c = gen.next();
 * }</pre>
 */
public final class CompanyGenerator {

    private final long seed;
    private final SplittableRandom random;
    private final DataSet data;
    private final boolean realCompanies;

    private CompanyGenerator(long seed, SplittableRandom random, DataSet data, boolean realCompanies) {
        this.seed = seed;
        this.random = random;
        this.data = data;
        this.realCompanies = realCompanies;
    }

    /**
     * Starts a builder with the defaults: a random seed, the bundled data,
     * and companies marked as not real.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The seed this generator started from. Pass it back to
     * {@link Builder#seed(long)} to get the same companies again.
     *
     * @return the seed
     */
    public long seed() {
        return seed;
    }

    /**
     * Generates the next company.
     *
     * @return a company from the list, marked unless {@code realCompanies} is on
     */
    public Company next() {
        return draw(data, realCompanies, random);
    }

    /**
     * Generates the next {@code count} companies.
     *
     * @param count how many, zero or more
     * @return a new mutable list of companies
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public List<Company> next(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be zero or more: " + count);
        }
        List<Company> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(next());
        }
        return out;
    }

    /**
     * An infinite, sequential stream of companies. Limit it, and don't call
     * {@code parallel()} on it: use {@link #split()} for parallel work.
     *
     * @return a stream drawing from this generator
     */
    public Stream<Company> stream() {
        return Stream.generate(this::next);
    }

    /**
     * A new generator with the same settings and its own reproducible
     * sequence, for another thread. Splitting draws from this generator, so it
     * changes the companies this one makes afterwards.
     *
     * @return the new generator
     */
    public CompanyGenerator split() {
        return new CompanyGenerator(seed, random.split(), data, realCompanies);
    }

    /**
     * A list of random companies, marked, with a random seed.
     *
     * @param howMany how many, zero or more
     * @return a new mutable list of companies
     * @throws IllegalArgumentException if {@code howMany} is negative
     */
    public static List<Company> generateListOfRandomCompanies(int howMany) {
        return builder().build().next(howMany);
    }

    /**
     * Draws one company uniformly and marks it unless {@code real}.
     * {@link PersonGenerator} calls this for the employer (step 6 of SPEC.md's
     * "How each field is made"), so the two draw the same way.
     */
    static Company draw(DataSet data, boolean real, SplittableRandom random) {
        return draw(data.companies, real, random);
    }

    /**
     * Draws one company uniformly from {@code companies} and marks it unless
     * {@code real}. One draw from {@code random}.
     */
    static Company draw(List<Company> companies, boolean real, SplittableRandom random) {
        Company c = companies.get(random.nextInt(companies.size()));
        return real ? c : new Company(c.name() + " [faux]", c.city(), c.state(), c.country(), exampleDomain(c.domain()));
    }

    /**
     * The domain under the reserved {@code .example} top-level domain
     * (RFC 2606), so an email at it can never be delivered: its first label
     * and {@code .example}, so {@code costco.com} becomes {@code costco.example}
     * and {@code sony.co.jp} becomes {@code sony.example}. The bundled files keep
     * every company's first label different, so their marked domains are too; a
     * data directory's own files must do the same, or two companies share one.
     */
    static String exampleDomain(String domain) {
        int dot = domain.indexOf('.');
        return (dot < 0 ? domain : domain.substring(0, dot)) + ".example";
    }

    /** Settings for a {@link CompanyGenerator}. */
    public static final class Builder {
        private Long seed;
        private Path dataDirectory;
        private boolean realCompanies;

        private Builder() {
        }

        /**
         * Fixes the seed, so the sequence of companies can be replayed.
         *
         * @param seed any value
         * @return this builder
         */
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * A folder of your own data files. Its {@code us_companies_domains.txt}
         * and {@code intl_companies_domains.txt}, if it has them, replace the
         * bundled lists, as {@link PersonGenerator.Builder#dataDirectory(Path)}
         * reads them. An empty {@code intl_companies_domains.txt} (or one with
         * only {@code #} lines) leaves the international companies out.
         *
         * @param dir a folder, or {@code null} for the bundled data only
         * @return this builder
         */
        public Builder dataDirectory(Path dir) {
            this.dataDirectory = dir;
            return this;
        }

        /**
         * Whether companies come as they are. By default ({@code false}) each
         * name ends with {@code " [faux]"} and each domain is under the reserved
         * {@code .example}, which never delivers mail. With {@code true} the
         * company has its real name and domain. Either way the same seed gives
         * the same companies.
         *
         * @param real {@code true} for real company names and domains
         * @return this builder
         */
        public Builder realCompanies(boolean real) {
            this.realCompanies = real;
            return this;
        }

        /**
         * Builds the generator.
         *
         * @return a new generator
         * @throws IllegalArgumentException if the data directory isn't a folder,
         *         or it holds a {@code .txt} file whose name isn't a data file's
         * @throws IllegalStateException    if a data file in the data directory is
         *         empty or has a bad line; the message names the file and line
         */
        public CompanyGenerator build() {
            long s = seed != null ? seed : new SplittableRandom().nextLong();
            DataSet data = dataDirectory == null ? DataSet.get() : DataSet.load(dataDirectory);
            return new CompanyGenerator(s, new SplittableRandom(s), data, realCompanies);
        }
    }
}
