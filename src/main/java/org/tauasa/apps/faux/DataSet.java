package org.tauasa.apps.faux;

import org.tauasa.apps.faux.model.Company;
import org.tauasa.apps.faux.model.Gender;
import org.tauasa.apps.faux.model.Region;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * The lists people are drawn from. The bundled ones are read once from the
 * classpath and shared by every generator. {@link #load(Path)} reads a folder
 * whose files replace bundled ones of the same name.
 *
 * <p>Each file is UTF-8, one entry per line, fields separated by {@code |}.
 * Blank lines and lines starting with {@code #} are skipped.
 */
final class DataSet {

    /**
     * A first name, the gender it is used for (F, M or U for either), and how
     * often it's drawn relative to the region's other first names.
     */
    record FirstName(String name, char gender, long weight) {}

    /** Names to draw from, with running totals of their weights. */
    private record Weighted<T>(List<T> items, long[] totals) {
        T pick(SplittableRandom random) {
            long ticket = random.nextLong(totals[totals.length - 1]);
            int i = Arrays.binarySearch(totals, ticket + 1);
            return items.get(i >= 0 ? i : -i - 1);
        }
    }

    /**
     * A surname in its masculine and feminine forms (the same for most languages),
     * and how often it's drawn relative to the region's other surnames.
     */
    record Surname(String masculine, String feminine, long weight) {}

    /** A street with the city, state and ZIP it runs through. */
    record Street(String street, String city, String state, String postalCode) {}

    final Map<Region, List<FirstName>> firstNames = new EnumMap<>(Region.class);
    final Map<Region, List<Surname>> surnames = new EnumMap<>(Region.class);
    /** Each region's first names for women (F and U), men (M and U) and anyone. */
    private final Map<Region, Weighted<FirstName>> femaleNames = new EnumMap<>(Region.class);
    private final Map<Region, Weighted<FirstName>> maleNames = new EnumMap<>(Region.class);
    private final Map<Region, Weighted<FirstName>> anyNames = new EnumMap<>(Region.class);
    private final Map<Region, Weighted<Surname>> weightedSurnames = new EnumMap<>(Region.class);
    final List<Company> companies;
    final List<Street> streets;

    /** Under the package, so the generic file names can't collide with another jar's. */
    private static final String RESOURCE_DIR = "/org/tauasa/apps/faux/data/";

    static final String COMPANIES = "us_companies_domains.txt";
    static final String STREETS = "us_street_names_city_state_zip.txt";

    private static volatile DataSet instance;

    /** Where files are read from first, or null for the bundled files only. */
    private final Path dir;

    static DataSet get() {
        DataSet d = instance;
        if (d == null) {
            synchronized (DataSet.class) {
                d = instance;
                if (d == null) {
                    instance = d = new DataSet(null);
                }
            }
        }
        return d;
    }

    /**
     * Reads each file from {@code dir} if it's there, and from the bundled
     * files if not. Every file is read again on each call.
     *
     * @throws IllegalArgumentException if {@code dir} isn't a folder, or holds a
     *         {@code .txt} file that isn't one of {@link #fileNames()}, which is
     *         usually a misspelled name that would otherwise be ignored
     * @throws IllegalStateException    if a file is empty or has a bad line
     */
    static DataSet load(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("data directory is not a folder: " + dir);
        }
        Set<String> unknown = new TreeSet<>();
        try (var files = Files.list(dir)) {
            files.map(f -> f.getFileName().toString())
                    .filter(n -> n.endsWith(".txt") && !fileNames().contains(n))
                    .forEach(unknown::add);
        } catch (IOException e) {
            throw new UncheckedIOException("listing " + dir, e);
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown data files in " + dir + ": " + unknown
                    + ". Names look like western_first_names.txt, rok_surnames.txt, "
                    + COMPANIES + " or " + STREETS);
        }
        return new DataSet(dir);
    }

    /** Every file name the generator reads, in order. */
    static List<String> fileNames() {
        List<String> names = new ArrayList<>();
        for (Region r : Region.values()) {
            String p = r.name().toLowerCase(Locale.ROOT);
            names.add(p + "_first_names.txt");
            names.add(p + "_surnames.txt");
        }
        names.add(COMPANIES);
        names.add(STREETS);
        return List.copyOf(names);
    }

    private DataSet(Path dir) {
        this.dir = dir;
        for (Region r : Region.values()) {
            String p = r.name().toLowerCase(Locale.ROOT);  // western_, asia_, rus_, rok_
            List<FirstName> fn = read(p + "_first_names.txt", 2, DataSet::firstName);
            firstNames.put(r, fn);
            femaleNames.put(r, weighted(fn.stream().filter(f -> f.gender() != 'M').toList(), FirstName::weight));
            maleNames.put(r, weighted(fn.stream().filter(f -> f.gender() != 'F').toList(), FirstName::weight));
            anyNames.put(r, weighted(fn, FirstName::weight));
            List<Surname> sn = read(p + "_surnames.txt", 1, DataSet::surname);
            surnames.put(r, sn);
            weightedSurnames.put(r, weighted(sn, Surname::weight));
        }
        companies = read(COMPANIES, 4, f -> new Company(f[0], f[1], f[2], f[3]));
        streets = read(STREETS, 4, f -> new Street(f[0], f[1], f[2], f[3]));
    }

    /** Null when the list is empty, so a pick from it can say which list. */
    private static <T> Weighted<T> weighted(List<T> items, ToLongFunction<T> weight) {
        if (items.isEmpty()) {
            return null;
        }
        long[] totals = new long[items.size()];
        long sum = 0;
        for (int i = 0; i < totals.length; i++) {
            sum = Math.addExact(sum, weight.applyAsLong(items.get(i)));
            totals[i] = sum;
        }
        return new Weighted<>(items, totals);
    }

    /**
     * Parses {@code name|gender}, with an optional weight last
     * ({@code Ji-hoon|M|97}). With no weight, the name has weight 1.
     */
    static FirstName firstName(String[] f) {
        char g = f[1].isEmpty() ? 'U' : f[1].charAt(0);
        if (f[1].length() > 1 || "FMU".indexOf(g) < 0) {
            throw new IllegalArgumentException("gender must be F, M or U: " + f[1]);
        }
        return new FirstName(f[0], g, f.length > 2 ? weight(f[2]) : 1);
    }

    private static long weight(String s) {
        long w;
        try {
            w = Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("weight must be a whole number: " + s);
        }
        if (w < 1) {
            throw new IllegalArgumentException("weight must be at least 1: " + s);
        }
        return w;
    }

    /**
     * Parses {@code name}, {@code masculine|feminine}, either with a weight last
     * ({@code Kim|6766}, {@code Petrov|Petrova|1663}). With no weight, the surname
     * has weight 1, so an unweighted file is drawn uniformly.
     */
    static Surname surname(String[] f) {
        int n = f.length;
        long weight = 1;
        if (n >= 2 && f[n - 1].matches("\\d+")) {
            weight = weight(f[n - 1]);
            n--;
        }
        String feminine = n > 1 && !f[1].isEmpty() ? f[1] : f[0];
        return new Surname(f[0], feminine, weight);
    }

    /** A surname from the region, each drawn in proportion to its weight. One draw from {@code random}. */
    Surname pickSurname(Region r, SplittableRandom random) {
        return weightedSurnames.get(r).pick(random);
    }

    /**
     * A first name from the region that suits the gender, each drawn in
     * proportion to its weight. Women get F and U names, men M and U names,
     * and nonbinary people any name. One draw from {@code random}.
     */
    FirstName pickFirstName(Region r, Gender gender, SplittableRandom random) {
        Weighted<FirstName> names = switch (gender) {
            case FEMALE -> femaleNames.get(r);
            case MALE -> maleNames.get(r);
            case NONBINARY -> anyNames.get(r);
        };
        if (names == null) {
            throw new IllegalStateException("no " + gender + " first names in the " + r + " list");
        }
        return names.pick(random);
    }

    private <T> List<T> read(String name, int minFields, Function<String[], T> parse) {
        Path own = dir == null ? null : dir.resolve(name);
        InputStream in;
        String file;
        if (own != null && Files.isRegularFile(own)) {
            file = own.toString();
            try {
                in = Files.newInputStream(own);
            } catch (IOException e) {
                throw new UncheckedIOException("reading " + own, e);
            }
        } else {
            file = name;
            in = DataSet.class.getResourceAsStream(RESOURCE_DIR + name);
            if (in == null) {
                throw new IllegalStateException("bundled data file missing from the classpath: " + RESOURCE_DIR + name);
            }
        }
        List<T> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int no = 0;
            while ((line = r.readLine()) != null) {
                no++;
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\\|", -1);
                if (f.length < minFields) {
                    throw new IllegalStateException(file + ":" + no + ": expected " + minFields
                            + " fields separated by |, got " + f.length);
                }
                for (int i = 0; i < f.length; i++) {
                    f[i] = f[i].strip();
                }
                try {
                    out.add(parse.apply(f));
                } catch (RuntimeException e) {
                    throw new IllegalStateException(file + ":" + no + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("reading " + file, e);
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("data file is empty: " + file);
        }
        return List.copyOf(out);
    }
}
