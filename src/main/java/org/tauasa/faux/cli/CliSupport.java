package org.tauasa.faux.cli;

import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.tauasa.faux.model.Region;

/** Argument parsing and output shared by the three command-line tools. */
final class CliSupport {

    enum Format { TEXT, PIPE, JSON }

    private CliSupport() {
    }

    static String value(String[] args, int i, String option) {
        if (i >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[i];
    }

    static long parseLong(String option, String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " must be a whole number: " + v);
        }
    }

    static int nonNegative(String option, String v) {
        long n = parseLong(option, v);
        if (n < 0 || n > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(option + " must be 0 or more: " + v);
        }
        return (int) n;
    }

    static Path parsePath(String option, String v) {
        try {
            return Path.of(v);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException(option + " is not a valid path: " + v);
        }
    }

    static Region parseRegion(String v) {
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

    /** "western, asia, ..., fin", from the enum, so a new region needs no edit here. */
    static String regionList() {
        return Arrays.stream(Region.values())
                .map(r -> r.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }

    static Format parseFormat(String v) {
        try {
            return Format.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("format must be text, pipe or json: " + v);
        }
    }

    /**
     * Prints {@code count} records in the format: one per line for text, a
     * header row then one row each for pipe, one array with a record per line
     * for JSON.
     */
    static <T> void print(PrintStream out, Format format, String pipeHeader, int count, Supplier<T> next,
                          Function<T, String> text, Function<T, String> pipe, Function<T, String> json) {
        if (format == Format.JSON) {
            out.println("[");
        } else if (format == Format.PIPE) {
            out.println(pipeHeader);
        }
        for (int i = 0; i < count; i++) {
            T t = next.get();
            switch (format) {
                case TEXT -> out.println(text.apply(t));
                case PIPE -> out.println(pipe.apply(t));
                case JSON -> out.println("  " + json.apply(t) + (i < count - 1 ? "," : ""));
            }
        }
        if (format == Format.JSON) {
            out.println("]");
        }
    }

    static String field(String name, String value) {
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
                    if (c < 0x20 || c == 0x7f || c == ' ' || c == ' ') {
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
