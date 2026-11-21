package org.tauasa.faux.cli;

import static org.tauasa.faux.cli.CliSupport.field;

import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import org.tauasa.faux.NameGenerator;
import org.tauasa.faux.cli.CliSupport.Format;
import org.tauasa.faux.model.Name;
import org.tauasa.faux.model.Region;

/**
 * Prints random names to standard output.
 *
 * <pre>
 * java -cp faux-person.jar org.tauasa.faux.cli.FauxNameCli -n 5 --seed 42 --region rus
 * </pre>
 */
public final class FauxNameCli {

    static final String USAGE = """
            usage: faux-name [options]

              -n, --count N        how many names to generate (default 10)
              -s, --seed S         seed, to get the same names again (default random)
              -r, --region R       %s or mixed (default mixed)
              -f, --format F       text, pipe or json (default text)
                  --data-dir DIR   read data files from DIR in place of the bundled
                                   ones with the same names (README.md, "Use your
                                   own data")
              -h, --help           show this help

            The seed in use is printed to standard error, so a run can be replayed.
            """.formatted(CliSupport.regionList());

    private FauxNameCli() {
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
        Region region = null;
        Format format = Format.TEXT;
        Path dataDir = null;
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
                    case "-r", "--region" -> region = CliSupport.parseRegion(CliSupport.value(args, ++i, a));
                    case "-f", "--format" -> format = CliSupport.parseFormat(CliSupport.value(args, ++i, a));
                    case "--data-dir" -> dataDir = CliSupport.parsePath(a, CliSupport.value(args, ++i, a));
                    default -> throw new IllegalArgumentException("unknown option: " + a);
                }
            }
            NameGenerator.Builder b = NameGenerator.builder().region(region).dataDirectory(dataDir);
            if (seed != null) {
                b.seed(seed);
            }
            NameGenerator gen = b.build();
            err.println("seed: " + gen.seed());
            CliSupport.print(out, format, "first|last", count, gen::next,
                    Name::fullName, FauxNameCli::pipe, FauxNameCli::json);
            return 0;
        } catch (IllegalArgumentException e) {
            err.println("faux-name: " + e.getMessage());
            err.print(USAGE);
            return 2;
        } catch (IllegalStateException | UncheckedIOException e) {
            err.println("faux-name: " + e.getMessage());
            return 1;
        }
    }

    static String pipe(Name n) {
        return n.firstName() + "|" + n.lastName();
    }

    static String json(Name n) {
        return "{" + field("firstName", n.firstName()) + "," + field("lastName", n.lastName()) + "}";
    }
}
