package org.tauasa.faux.cli;

import static org.tauasa.faux.cli.CliSupport.field;

import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import org.tauasa.faux.CompanyGenerator;
import org.tauasa.faux.cli.CliSupport.Format;
import org.tauasa.faux.model.Company;

/**
 * Prints random companies to standard output.
 *
 * <pre>
 * java -cp faux-person.jar org.tauasa.faux.cli.FauxCompanyCli -n 5 --seed 42 --format json
 * </pre>
 */
public final class FauxCompanyCli {

    static final String USAGE = """
            usage: faux-company [options]

              -n, --count N        how many companies to generate (default 10)
              -s, --seed S         seed, to get the same companies again (default random)
              -f, --format F       text, pipe or json (default text)
                  --data-dir DIR   read us_companies_domains.txt from DIR in place
                                   of the bundled one (README.md, "Use your own data")
                  --real-companies real company names and domains (default: names
                                   end in [faux] and domains are under .example,
                                   which never delivers)
              -h, --help           show this help

            The seed in use is printed to standard error, so a run can be replayed.
            """;

    private FauxCompanyCli() {
    }

    /**
     * Entry point.
     *
     * @param args command-line arguments; {@code --help} lists them
     */
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** Runs the CLI and returns its exit code: 0 ok, 1 a bad data file, 2 bad arguments. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        int count = 10;
        Long seed = null;
        Format format = Format.TEXT;
        Path dataDir = null;
        boolean realCompanies = false;
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "-h", "--help" -> {
                        out.print(USAGE);
                        return 0;
                    }
                    case "-n", "--count" -> count = CliSupport.nonNegative(a, CliSupport.value(args, ++i, a));
                    case "-s", "--seed" -> seed = CliSupport.parseLong(a, CliSupport.value(args, ++i, a));
                    case "-f", "--format" -> format = CliSupport.parseFormat(CliSupport.value(args, ++i, a));
                    case "--data-dir" -> dataDir = CliSupport.parsePath(a, CliSupport.value(args, ++i, a));
                    case "--real-companies" -> realCompanies = true;
                    default -> throw new IllegalArgumentException("unknown option: " + a);
                }
            }
            CompanyGenerator.Builder b = CompanyGenerator.builder()
                    .dataDirectory(dataDir).realCompanies(realCompanies);
            if (seed != null) {
                b.seed(seed);
            }
            CompanyGenerator gen = b.build();
            err.println("seed: " + gen.seed());
            CliSupport.print(out, format, "name|city|state|domain|country", count, gen::next,
                    FauxCompanyCli::text, FauxCompanyCli::pipe, FauxCompanyCli::json);
            return 0;
        } catch (IllegalArgumentException e) {
            err.println("faux-company: " + e.getMessage());
            err.print(USAGE);
            return 2;
        } catch (IllegalStateException | UncheckedIOException e) {
            err.println("faux-company: " + e.getMessage());
            return 1;
        }
    }

    /** {@code Costco [faux], Issaquah, WA (costco.example)}; outside the US the country takes the state's place. */
    static String text(Company c) {
        var a = c.address();
        return c.name() + ", " + a.city() + ", " + (a.state().isEmpty() ? a.country() : a.state())
                + " (" + c.domain() + ")";
    }

    static String pipe(Company c) {
        var a = c.address();
        return String.join("|", c.name(), a.city(), a.state(), c.domain(), a.country());
    }

    static String json(Company c) {
        return "{" + field("name", c.name()) + "," + CliSupport.addressJson(c.address())
                + "," + field("domain", c.domain()) + "}";
    }
}
