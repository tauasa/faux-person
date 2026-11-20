package org.tauasa.apps.faux.cli;

import java.io.PrintStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.tauasa.apps.faux.PersonGenerator;
import org.tauasa.apps.faux.model.Person;
import org.tauasa.apps.faux.model.Region;

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

              -n, --count N        how many people to print (default 10)
              -s, --seed S         seed, to get the same people again (default random)
              -r, --region R       %s or mixed (default mixed)
                  --min-age N      youngest age (default 18)
                  --max-age N      oldest age (default 90)
                  --today DATE     the date ages are counted from, as YYYY-MM-DD
                                   (default today); fix it to get the same birth
                                   dates every day
              -f, --format F       text, pipe or json (default text)
              -h, --help           show this help

            The seed in use is printed to standard error, so a run can be replayed.
            """.formatted(regionList());

    enum Format { TEXT, PIPE, JSON }

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

    /** Runs the CLI and returns its exit code: 0 ok, 2 bad arguments. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        int count = 10;
        Long seed = null;
        Region region = null;
        int minAge = 18;
        int maxAge = 90;
        Format format = Format.TEXT;
        LocalDate today = null;
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "-h", "--help" -> {
                        out.print(USAGE);
                        return 0;
                    }
                    case "-n", "--count" -> count = nonNegative(a, value(args, ++i, a));
                    case "-s", "--seed" -> seed = parseLong(a, value(args, ++i, a));
                    case "-r", "--region" -> region = parseRegion(value(args, ++i, a));
                    case "--min-age" -> minAge = nonNegative(a, value(args, ++i, a));
                    case "--max-age" -> maxAge = nonNegative(a, value(args, ++i, a));
                    case "-f", "--format" -> format = parseFormat(value(args, ++i, a));
                    case "--today" -> today = parseDate(a, value(args, ++i, a));
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
                    .region(region).ageRange(minAge, maxAge).clock(clock);
            if (seed != null) {
                b.seed(seed);
            }
            PersonGenerator gen = b.build();
            err.println("seed: " + gen.seed());
            if (format == Format.JSON) {
                out.println("[");
            } else if (format == Format.PIPE) {
                out.println("first|last|gender|region|dob|age|email|phone|street|city|state|zip|employer");
            }
            for (int i = 0; i < count; i++) {
                Person p = gen.next();
                switch (format) {
                    case TEXT -> out.println(text(p, today));
                    case PIPE -> out.println(pipe(p, today));
                    case JSON -> out.println("  " + json(p, today) + (i < count - 1 ? "," : ""));
                }
            }
            if (format == Format.JSON) {
                out.println("]");
            }
            return 0;
        } catch (IllegalArgumentException e) {
            err.println("faux-person: " + e.getMessage());
            err.print(USAGE);
            return 2;
        }
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[i];
    }

    private static long parseLong(String option, String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " must be a whole number: " + v);
        }
    }

    private static int nonNegative(String option, String v) {
        long n = parseLong(option, v);
        if (n < 0 || n > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(option + " must be 0 or more: " + v);
        }
        return (int) n;
    }

    private static LocalDate parseDate(String option, String v) {
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(option + " must be a date like 2026-11-20: " + v);
        }
    }

    private static Region parseRegion(String v) {
        String s = v.toLowerCase(Locale.ROOT);
        if (s.equals("mixed")) {
            return null;
        }
        for (Region r : Region.values()) {
            if (r.name().equalsIgnoreCase(s)) {
                return r;
            }
        }
        throw new IllegalArgumentException("region must be " + regionList() + " or mixed: " + v);
    }

    /** "western, asia, ..., pse", from the enum, so a new region needs no edit here. */
    private static String regionList() {
        return java.util.Arrays.stream(Region.values())
                .map(r -> r.name().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static Format parseFormat(String v) {
        try {
            return Format.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("format must be text, pipe or json: " + v);
        }
    }

    static String text(Person p, LocalDate today) {
        var a = p.address();
        return String.format(Locale.ROOT, "%s %s (%s, %d) <%s> %s, %s, %s, %s %s, works at %s",
                p.firstName(), p.lastName(), p.gender().name().toLowerCase(Locale.ROOT), p.ageOn(today),
                p.email(), p.phone(), a.street(), a.city(), a.state(), a.postalCode(), p.employer().name());
    }

    static String pipe(Person p, LocalDate today) {
        var a = p.address();
        return String.join("|", p.firstName(), p.lastName(), p.gender().name(), p.region().name(),
                p.dateOfBirth().toString(), Integer.toString(p.ageOn(today)), p.email(), p.phone(),
                a.street(), a.city(), a.state(), a.postalCode(), p.employer().name());
    }

    static String json(Person p, LocalDate today) {
        var a = p.address();
        var e = p.employer();
        return "{" + field("firstName", p.firstName()) + "," + field("lastName", p.lastName())
                + "," + field("gender", p.gender().name()) + "," + field("region", p.region().name())
                + "," + field("dateOfBirth", p.dateOfBirth().toString()) + ",\"age\":" + p.ageOn(today)
                + "," + field("email", p.email()) + "," + field("phone", p.phone())
                + ",\"address\":{" + field("street", a.street()) + "," + field("city", a.city())
                + "," + field("state", a.state()) + "," + field("postalCode", a.postalCode())
                + "," + field("country", a.country()) + "}"
                + ",\"employer\":{" + field("name", e.name()) + "," + field("city", e.city())
                + "," + field("state", e.state()) + "," + field("domain", e.domain()) + "}}";
    }

    private static String field(String name, String value) {
        return "\"" + name + "\":" + quote(value);
    }

    static String quote(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    // Below 0x20 is required by JSON. DEL and the two line separators
                    // are legal JSON but break a <script> block or an older JS engine.
                    if (c < 0x20 || c == 0x7f || c == '\u2028' || c == '\u2029') {
                        b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }
}
