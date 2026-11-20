package org.tauasa.apps.faux;

import org.tauasa.apps.faux.model.Company;
import org.tauasa.apps.faux.model.Region;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The bundled lists, read once from the classpath and shared by every generator.
 *
 * <p>Each file is UTF-8, one entry per line, fields separated by {@code |}.
 * Blank lines and lines starting with {@code #} are skipped.
 */
final class DataSet {

    /** A first name and the gender it is used for: F, M or U (either). */
    record FirstName(String name, char gender) {}

    /** A surname in its masculine and feminine forms (the same for most languages). */
    record Surname(String masculine, String feminine) {}

    /** A street with the city, state and ZIP it runs through. */
    record Street(String street, String city, String state, String postalCode) {}

    /** A city with its state and one of its ZIP codes. */
    record City(String city, String state, String postalCode) {}

    final Map<Region, List<FirstName>> firstNames = new EnumMap<>(Region.class);
    final Map<Region, List<Surname>> surnames = new EnumMap<>(Region.class);
    final List<Company> companies;
    final List<Street> streets;
    final List<City> cities;

    /** Under the package, so the generic file names can't collide with another jar's. */
    private static final String RESOURCE_DIR = "/org/tauasa/apps/faux/data/";

    private static volatile DataSet instance;

    static DataSet get() {
        DataSet d = instance;
        if (d == null) {
            synchronized (DataSet.class) {
                d = instance;
                if (d == null) {
                    instance = d = new DataSet();
                }
            }
        }
        return d;
    }

    private DataSet() {
        for (Region r : Region.values()) {
            String p = r.name().toLowerCase(Locale.ROOT);  // western_, asia_, rus_, rok_
            firstNames.put(r, read(p + "_first_names.txt", 2, f -> {
                char g = f[1].isEmpty() ? 'U' : f[1].charAt(0);
                if ("FMU".indexOf(g) < 0) {
                    throw new IllegalArgumentException("gender must be F, M or U: " + f[1]);
                }
                return new FirstName(f[0], g);
            }));
            // rus_surnames has masculine|feminine; the others have one column.
            surnames.put(r, read(p + "_surnames.txt", 1,
                    f -> new Surname(f[0], f.length > 1 && !f[1].isEmpty() ? f[1] : f[0])));
        }
        companies = read("us_companies_domains.txt", 4, f -> new Company(f[0], f[1], f[2], f[3]));
        streets = read("us_street_names_city_state_zip.txt", 4, f -> new Street(f[0], f[1], f[2], f[3]));
        cities = read("us_cities_states_zip.txt", 3, f -> new City(f[0], f[1], f[2]));
    }

    private static <T> List<T> read(String file, int minFields, Function<String[], T> parse) {
        InputStream in = DataSet.class.getResourceAsStream(RESOURCE_DIR + file);
        if (in == null) {
            throw new IllegalStateException("bundled data file missing from the classpath: " + RESOURCE_DIR + file);
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
            throw new IllegalStateException("bundled data file is empty: " + file);
        }
        return List.copyOf(out);
    }
}
