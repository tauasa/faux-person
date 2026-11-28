package org.tauasa.faux;

import org.tauasa.faux.model.Address;
import org.tauasa.faux.model.Company;
import org.tauasa.faux.model.Gender;
import org.tauasa.faux.model.Region;
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
     * A first name, the gender it is used for (F, M or U for either), how
     * often it's drawn relative to the region's other first names, and the
     * name in its own script, or null when the file has none.
     */
    record FirstName(String name, char gender, long weight, String nativeName) {
        FirstName(String name, char gender, long weight) {
            this(name, gender, weight, null);
        }
    }

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
     * how often it's drawn relative to the region's other surnames, and the
     * surname in its own script: {@code nativeName} for the masculine form and
     * {@code nativeFeminine} for the feminine one (Петров, Петрова). Either is
     * null when the file has none. When the two Latin forms are the same, so are
     * the native ones.
     */
    record Surname(String masculine, String feminine, long weight, String nativeName, String nativeFeminine) {
        Surname(String masculine, String feminine, long weight) {
            this(masculine, feminine, weight, null, null);
        }

        Surname(String masculine, String feminine, long weight, String nativeName) {
            this(masculine, feminine, weight, nativeName, feminine.equals(masculine) ? nativeName : null);
        }
    }

    /**
     * A street with the city, state and postal code it runs through. Outside
     * the US the state is empty, and so is the postal code where GeoNames has
     * none near it; the street is empty where the country has city lines only.
     */
    record Street(String street, String city, String state, String postalCode) {}

    final Map<Region, List<FirstName>> firstNames = new EnumMap<>(Region.class);
    final Map<Region, List<Surname>> surnames = new EnumMap<>(Region.class);
    /** Each region's first names for women (F and U) and for men (M and U). */
    private final Map<Region, Weighted<FirstName>> femaleNames = new EnumMap<>(Region.class);
    private final Map<Region, Weighted<FirstName>> maleNames = new EnumMap<>(Region.class);
    private final Map<Region, Weighted<Surname>> weightedSurnames = new EnumMap<>(Region.class);
    /** Every company: the US ones, then the international ones. */
    final List<Company> companies;
    /** Each region's employers: its countries' companies, or every company (see {@link #employers}). */
    private final Map<Region, List<Company>> employers = new EnumMap<>(Region.class);
    /** US streets. */
    final List<Street> streets;
    /** Streets outside the US, by ISO 3166-1 alpha-2 country code. */
    private final Map<String, List<Street>> intlStreets;

    /** Under the package, so the generic file names can't collide with another jar's. */
    private static final String RESOURCE_DIR = "/org/tauasa/faux/data/";

    static final String COMPANIES = "us_companies_domains.txt";
    static final String INTL_COMPANIES = "intl_companies_domains.txt";

    /**
     * Each region's countries, as ISO 3166-1 alpha-2 codes: the countries its
     * names come from. {@code WESTERN} isn't here: its people work anywhere.
     */
    static final Map<Region, Set<String>> COUNTRIES = Map.ofEntries(
            Map.entry(Region.ASIA, Set.of("BD", "CN", "HK", "ID", "IN", "JP", "KH", "KR", "LA", "LK", "MM", "MN",
                    "MY", "NP", "PH", "PK", "SG", "TH", "TW", "VN")),
            Map.entry(Region.RUS, Set.of("AM", "AZ", "BY", "EE", "GE", "KG", "KZ", "LT", "LV", "MD", "RU", "TJ",
                    "TM", "UA", "UZ")),
            Map.entry(Region.ROK, Set.of("KR")), Map.entry(Region.MEX, Set.of("MX")),
            Map.entry(Region.IRN, Set.of("IR")), Map.entry(Region.HEB, Set.of("IL")),
            Map.entry(Region.PSE, Set.of("PS")), Map.entry(Region.JPN, Set.of("JP")),
            Map.entry(Region.PHL, Set.of("PH")), Map.entry(Region.VNM, Set.of("VN")),
            Map.entry(Region.THA, Set.of("TH")),
            Map.entry(Region.NAF, Set.of("DZ", "EG", "EH", "LY", "MA", "SD", "TN")),
            Map.entry(Region.CAF, Set.of("AO", "CD", "CF", "CG", "CM", "GA", "GQ", "ST", "TD")),
            Map.entry(Region.SAF, Set.of("BW", "LS", "NA", "SZ", "ZA")),
            Map.entry(Region.BRA, Set.of("BR")), Map.entry(Region.GBR, Set.of("GB")),
            Map.entry(Region.FRA, Set.of("FR")), Map.entry(Region.ESP, Set.of("ES")),
            Map.entry(Region.PRT, Set.of("PT")), Map.entry(Region.GRC, Set.of("GR")),
            Map.entry(Region.NOR, Set.of("NO")), Map.entry(Region.SWE, Set.of("SE")),
            Map.entry(Region.FIN, Set.of("FI")));

    /** Fewer companies than this in a region's countries, and its people draw from {@link #NEARBY}. */
    static final int MIN_EMPLOYERS = 20;

    /**
     * Where a region with too few companies of its own looks next: its own
     * countries and these. Palestine to the Arab Middle East, Central Africa to
     * all of Africa. A region not listed here, or still short, draws from every
     * company.
     */
    static final Map<Region, Set<String>> NEARBY = Map.of(
            Region.PSE, Set.of("PS", "JO", "LB", "SY", "IQ", "EG", "SA", "AE", "QA", "KW", "BH", "OM", "YE"),
            Region.CAF, Set.of("DZ", "AO", "BJ", "BW", "BF", "BI", "CV", "CM", "CF", "TD", "KM", "CG", "CD",
                    "CI", "DJ", "EG", "GQ", "ER", "SZ", "ET", "GA", "GM", "GH", "GN", "GW", "KE", "LS", "LR",
                    "LY", "MG", "MW", "ML", "MR", "MU", "MA", "MZ", "NA", "NE", "NG", "RW", "ST", "SN", "SC",
                    "SL", "SO", "ZA", "SS", "SD", "TZ", "TG", "TN", "UG", "ZM", "ZW", "EH"));
    static final String STREETS = "us_street_names_city_state_zip.txt";
    static final String INTL_STREETS = "intl_streets.txt";

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
        names.add(INTL_COMPANIES);
        names.add(STREETS);
        names.add(INTL_STREETS);
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
            List<Surname> sn = read(p + "_surnames.txt", 1, DataSet::surname);
            surnames.put(r, sn);
            weightedSurnames.put(r, weighted(sn, Surname::weight));
        }
        List<Company> all = new ArrayList<>(read(COMPANIES, 4,
                f -> new Company(f[0], new Address("", f[1], f[2], "", "US"), f[3])));
        // May be empty: a data directory's empty file turns the international companies off.
        all.addAll(read(INTL_COMPANIES, 4, true,
                f -> new Company(f[0], new Address("", f[1], "", "", f[2]), f[3])));
        companies = List.copyOf(all);
        for (Region r : Region.values()) {
            employers.put(r, employersFor(r));
        }
        streets = read(STREETS, 4, f -> new Street(f[0], f[1], f[2], f[3]));
        // May be empty too: then a person abroad lives in their employer's headquarters city.
        Map<String, List<Street>> abroad = new java.util.TreeMap<>();
        for (String[] f : read(INTL_STREETS, 4, true, f -> f)) {
            if (f[0].length() != 2 || f[2].isEmpty()) {
                throw new IllegalStateException(INTL_STREETS + ": need a two-letter country and a city: "
                        + String.join("|", f));
            }
            abroad.computeIfAbsent(f[0], k -> new ArrayList<>()).add(new Street(f[1], f[2], "", f[3]));
        }
        abroad.replaceAll((k, v) -> List.copyOf(v));
        intlStreets = Map.copyOf(abroad);
    }

    /**
     * The streets of a country other than the US, in file order, or an empty
     * list if the data has none for it.
     */
    List<Street> intlStreets(String country) {
        return intlStreets.getOrDefault(country, List.of());
    }

    /**
     * The companies a person from the region works for: those headquartered in
     * the region's countries; for a region with fewer than {@link #MIN_EMPLOYERS},
     * those in its {@link #NEARBY} countries; and every company for {@code WESTERN}
     * and when that is still too few.
     */
    List<Company> employers(Region r) {
        return employers.get(r);
    }

    private List<Company> employersFor(Region r) {
        for (Set<String> countries : java.util.Arrays.asList(COUNTRIES.get(r), NEARBY.get(r))) {
            if (countries == null) {
                continue;
            }
            List<Company> some = companies.stream().filter(c -> countries.contains(c.address().country())).toList();
            if (some.size() >= MIN_EMPLOYERS) {
                return some;
            }
        }
        return companies;
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
     * Parses {@code name|gender}, with an optional weight
     * ({@code Ji-hoon|M|97}) and then an optional name in its own script
     * ({@code Ji-hoon|M|97|지훈}). With no weight, the name has weight 1.
     */
    static FirstName firstName(String[] f) {
        char g = f[1].isEmpty() ? 'U' : f[1].charAt(0);
        if (f[1].length() > 1 || "FMU".indexOf(g) < 0) {
            throw new IllegalArgumentException("gender must be F, M or U: " + f[1]);
        }
        return new FirstName(f[0], g, f.length > 2 ? weight(f[2]) : 1, f.length > 3 ? nativeName(f[3]) : null);
    }

    /** A native-script field: present and not blank, since the field is only there to give one. */
    private static String nativeName(String s) {
        if (s.isEmpty()) {
            throw new IllegalArgumentException("the native name, when given, must not be empty");
        }
        return s;
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
     * ({@code Kim|6768}, {@code Petrov|Petrova|906}). With no weight, the surname
     * has weight 1, so an unweighted file is drawn uniformly. Four fields add the
     * surname in its own script: {@code masculine|feminine|weight|native}, with
     * the feminine form left empty when it's the same ({@code Kim||6768|김}).
     * A fifth gives the feminine form's ({@code Petrov|Petrova|906|Петров|Петрова});
     * without it, a feminine form that differs has no native spelling.
     */
    static Surname surname(String[] f) {
        if (f.length >= 4) {
            String feminine = f[1].isEmpty() ? f[0] : f[1];
            String nativeMasculine = nativeName(f[3]);
            String nativeFeminine = f.length >= 5 ? nativeName(f[4])
                    : feminine.equals(f[0]) ? nativeMasculine : null;
            return new Surname(f[0], feminine, weight(f[2]), nativeMasculine, nativeFeminine);
        }
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
     * proportion to its weight. Women get F and U names and men M and U
     * names. A nonbinary person gets a woman's or a man's name at 50/50, the
     * way their surname is chosen, so Wikidata's surplus of men doesn't tilt
     * them toward men's names. One draw from {@code random}, two for nonbinary.
     */
    FirstName pickFirstName(Region r, Gender gender, SplittableRandom random) {
        Weighted<FirstName> names = switch (gender) {
            case FEMALE -> femaleNames.get(r);
            case MALE -> maleNames.get(r);
            case NONBINARY -> random.nextBoolean() ? femaleNames.get(r) : maleNames.get(r);
        };
        if (names == null) {
            throw new IllegalStateException("no " + gender + " first names in the " + r + " list");
        }
        return names.pick(random);
    }

    private <T> List<T> read(String name, int minFields, Function<String[], T> parse) {
        return read(name, minFields, false, parse);
    }

    private <T> List<T> read(String name, int minFields, boolean mayBeEmpty, Function<String[], T> parse) {
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
        if (out.isEmpty() && !mayBeEmpty) {
            throw new IllegalStateException("data file is empty: " + file);
        }
        return List.copyOf(out);
    }
}
