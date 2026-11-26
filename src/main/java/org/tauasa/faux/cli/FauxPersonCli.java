package org.tauasa.faux.cli;

import static org.tauasa.faux.cli.CliSupport.field;

import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.tauasa.faux.PersonGenerator;
import org.tauasa.faux.cli.CliSupport.Format;
import org.tauasa.faux.model.Person;
import org.tauasa.faux.model.Region;

/**
 * Prints random people to standard output.
 *
 * <pre>
 * java -jar faux-person.jar -n 5 --seed 42 --region asia --format json
 * </pre>
 */
public final class FauxPersonCli {

    static final String USAGE = """
            usage: faux-person [options]

              -n, --count N        how many people to generate (default 10)
              -s, --seed S         seed, to get the same people again (default random)
              -r, --region R       %s or mixed (default mixed)
                  --min-age N      youngest age (default 18)
                  --max-age N      oldest age (default 90)
                  --today DATE     the date ages are counted from, as YYYY-MM-DD
                                   (default today); fix it to get the same birth
                                   dates every day
              -f, --format F       text, pipe or json (default text)
                  --data-dir DIR   read data files from DIR in place of the bundled
                                   ones with the same names (README.md, "Use your
                                   own data")
                  --real-companies employers and emails use real company names
                                   and domains (default: names end in [faux]
                                   and emails are under .example, which never
                                   delivers)
              -h, --help           show this help

            The seed in use is printed to standard error, so a run can be replayed.
            """.formatted(CliSupport.regionList());

    private FauxPersonCli() {
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
        int minAge = 18;
        int maxAge = 90;
        Format format = Format.TEXT;
        LocalDate today = null;
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
                    case "-r", "--region" -> region = CliSupport.parseRegion(CliSupport.value(args, ++i, a));
                    case "--min-age" -> minAge = CliSupport.nonNegative(a, CliSupport.value(args, ++i, a));
                    case "--max-age" -> maxAge = CliSupport.nonNegative(a, CliSupport.value(args, ++i, a));
                    case "-f", "--format" -> format = CliSupport.parseFormat(CliSupport.value(args, ++i, a));
                    case "--today" -> today = parseDate(a, CliSupport.value(args, ++i, a));
                    case "--data-dir" -> dataDir = CliSupport.parsePath(a, CliSupport.value(args, ++i, a));
                    case "--real-companies" -> realCompanies = true;
                    default -> throw new IllegalArgumentException("unknown option: " + a);
                }
            }
            if (today == null) {
                today = LocalDate.now();
            }
            // One fixed day for both the birth dates and the ages printed, so the two
            // always agree, even if a run crosses midnight.
            Clock clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
            PersonGenerator.Builder b = PersonGenerator.builder()
                    .region(region).ageRange(minAge, maxAge).clock(clock).dataDirectory(dataDir)
                    .realCompanies(realCompanies);
            if (seed != null) {
                b.seed(seed);
            }
            PersonGenerator gen = b.build();
            err.println("seed: " + gen.seed());
            LocalDate on = today;
            CliSupport.print(out, format,
                    "first|last|gender|region|dob|age|email|phone|street|city|state|zip|employer|native",
                    count, gen::next, p -> text(p, on), p -> pipe(p, on), p -> json(p, on));
            return 0;
        } catch (IllegalArgumentException e) {
            err.println("faux-person: " + e.getMessage());
            err.print(USAGE);
            return 2;
        } catch (IllegalStateException | UncheckedIOException e) {
            // A data file in --data-dir is empty, has a bad line or can't be read.
            err.println("faux-person: " + e.getMessage());
            return 1;
        }
    }

    private static LocalDate parseDate(String option, String v) {
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(option + " must be a date like 2026-11-20: " + v);
        }
    }

    /** {@code Ji-hoon Kim (김지훈, male, 34) ...}: the native form leads the brackets when there is one. */
    static String text(Person p, LocalDate today) {
        var a = p.address();
        String nat = CliSupport.nativeFull(p.name());
        return String.format(Locale.ROOT, "%s %s (%s%s, %d) <%s> %s, %s, %s, %s %s, works at %s",
                p.firstName(), p.lastName(), nat.isEmpty() ? "" : nat + ", ",
                p.gender().name().toLowerCase(Locale.ROOT), p.ageOn(today),
                p.email(), p.phone(), a.street(), a.city(), a.state(), a.postalCode(), p.employer().name());
    }

    static String pipe(Person p, LocalDate today) {
        var a = p.address();
        return String.join("|", p.firstName(), p.lastName(), p.gender().name(), p.region().name(),
                p.dateOfBirth().toString(), Integer.toString(p.ageOn(today)), p.email(), p.phone(),
                a.street(), a.city(), a.state(), a.postalCode(), p.employer().name(), CliSupport.nativeFull(p.name()));
    }

    static String json(Person p, LocalDate today) {
        var a = p.address();
        var e = p.employer();
        return "{" + field("firstName", p.firstName()) + "," + field("lastName", p.lastName())
                + CliSupport.nativeJson(p.name())
                + "," + field("gender", p.gender().name()) + "," + field("region", p.region().name())
                + "," + field("dateOfBirth", p.dateOfBirth().toString()) + ",\"age\":" + p.ageOn(today)
                + "," + field("email", p.email()) + "," + field("phone", p.phone())
                + ",\"address\":{" + field("street", a.street()) + "," + field("city", a.city())
                + "," + field("state", a.state()) + "," + field("postalCode", a.postalCode())
                + "," + field("country", a.country()) + "}"
                + ",\"employer\":{" + field("name", e.name()) + "," + field("city", e.city())
                + "," + field("state", e.state()) + "," + field("domain", e.domain()) + "}}";
    }
}
