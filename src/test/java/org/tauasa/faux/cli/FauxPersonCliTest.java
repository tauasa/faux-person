package org.tauasa.faux.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FauxPersonCliTest {

    private String out;
    private String err;

    private int run(String... args) {
        var o = new ByteArrayOutputStream();
        var e = new ByteArrayOutputStream();
        int code = FauxPersonCli.run(args, new PrintStream(o, true, StandardCharsets.UTF_8),
                new PrintStream(e, true, StandardCharsets.UTF_8));
        out = o.toString(StandardCharsets.UTF_8);
        err = e.toString(StandardCharsets.UTF_8);
        return code;
    }

    @Test
    void printsTheRequestedNumberOfPeople() {
        assertEquals(0, run("-n", "7", "--seed", "1"));
        assertEquals(7, out.lines().count());
        assertTrue(err.contains("seed: 1"), err);
    }

    @Test
    void realCompaniesSwitchUsesRealNamesAndDomains() {
        assertEquals(0, run("-n", "50", "-s", "4", "-f", "pipe", "--today", "2026-11-20"));
        var marked = out.lines().skip(1).toList();
        assertEquals(0, run("-n", "50", "-s", "4", "-f", "pipe", "--today", "2026-11-20", "--real-companies"));
        var real = out.lines().skip(1).toList();
        for (int i = 0; i < marked.size(); i++) {
            String[] m = marked.get(i).split("\\|"), r = real.get(i).split("\\|");
            assertTrue(m[6].endsWith(".example"), m[6]);
            assertTrue(!r[6].endsWith(".example"), r[6]);
            assertEquals(r[12] + " [faux]", m[12]);
        }
        assertTrue(FauxPersonCli.USAGE.contains("--real-companies"));
    }

    @Test
    void sameSeedPrintsTheSameOutput() {
        run("-n", "5", "-s", "99", "-f", "pipe");
        String first = out;
        run("-n", "5", "-s", "99", "-f", "pipe");
        assertEquals(first, out);
    }

    @Test
    void todayFixesBirthDatesAndAges() {
        run("-n", "20", "-s", "5", "-f", "pipe", "--today", "2026-11-20");
        String first = out;
        run("-n", "20", "-s", "5", "-f", "pipe", "--today", "2026-11-20");
        assertEquals(first, out);
        // Another day changes only the birth dates and ages: the date window's
        // length changes with the calendar, so the same draw lands elsewhere in
        // it. Every other column stays the same.
        run("-n", "20", "-s", "5", "-f", "pipe", "--today", "2027-11-20");
        var now = first.lines().skip(1).toList();
        var later = out.lines().skip(1).toList();
        assertEquals(now.size(), later.size());
        for (int i = 0; i < now.size(); i++) {
            String[] x = now.get(i).split("\\|"), y = later.get(i).split("\\|");
            for (int col = 0; col < x.length; col++) {
                if (col != 4 && col != 5) {
                    assertEquals(x[col], y[col], "column " + col + ": " + later.get(i));
                }
            }
        }
        assertTrue(!first.equals(out), "a different --today should move the birth dates");
        // Ages are counted from --today, and stay inside the age range on that day.
        run("-n", "200", "-s", "6", "-f", "pipe", "--today", "2000-02-29", "--min-age", "30", "--max-age", "30");
        out.lines().skip(1).forEach(l -> {
            String[] f = l.split("\\|");
            assertEquals("30", f[5], l);
            assertEquals(30, java.time.Period.between(java.time.LocalDate.parse(f[4]),
                    java.time.LocalDate.of(2000, 2, 29)).getYears(), l);
        });
    }

    @Test
    void pipeFormatHasAHeaderAndFourteenColumns() {
        assertEquals(0, run("-n", "3", "-s", "2", "--format", "pipe"));
        var lines = out.lines().toList();
        assertEquals(4, lines.size());
        assertTrue(lines.get(0).endsWith("|employer|native"), lines.get(0));
        lines.forEach(l -> assertEquals(14, l.split("\\|", -1).length, l));
    }

    /** A South Korean's Hangul name leads the text brackets, fills the last pipe column and is a JSON object. */
    @Test
    void koreansShowTheirHangulInEachFormat() {
        assertEquals(0, run("-n", "3", "-s", "2", "-r", "rok", "--today", "2026-11-20"));
        out.lines().forEach(l -> assertTrue(l.matches("[A-Za-z' -]+ \\([\\uAC00-\\uD7A3]{2,6}, \\w+, \\d+\\) <.*"), l));
        assertEquals(0, run("-n", "3", "-s", "2", "-r", "rok", "-f", "pipe"));
        out.lines().skip(1).forEach(l -> assertTrue(l.matches(".*\\|[\\uAC00-\\uD7A3]{2,6}"), l));
        assertEquals(0, run("-n", "3", "-s", "2", "-r", "rok", "-f", "json"));
        assertEquals(3, out.split("\"nativeName\":\\{\"firstName\":\"[^\"]+\",\"lastName\":\"[^\"]+\",\"fullName\":\"").length - 1, out);
        assertEquals(0, run("-n", "3", "-s", "2", "-r", "gbr", "-f", "json"));
        assertTrue(!out.contains("nativeName"), out);
        assertEquals(0, run("-n", "3", "-s", "2", "-r", "gbr", "-f", "pipe"));
        out.lines().skip(1).forEach(l -> assertTrue(l.endsWith("|"), l));
    }

    /**
     * Golden: the 13 Latin-letter columns of seed 42's first 1,000 people, as 0.3.0
     * printed them. Native names are only added beside them, so this fails when
     * anything changes who a seed draws. Update the hash only with a CHANGELOG line
     * saying what changed.
     */
    @Test
    void seed42sPeopleAreTheSameAs030() throws Exception {
        assertEquals(0, run("-n", "1000", "-s", "42", "--today", "2026-11-20", "-f", "pipe"));
        String latin = String.join("\n", out.lines().skip(1)
                .map(l -> String.join("|", java.util.Arrays.copyOf(l.split("\\|", -1), 13))).toList());
        byte[] sha = java.security.MessageDigest.getInstance("SHA-256").digest(latin.getBytes(StandardCharsets.UTF_8));
        assertEquals("ae15c10aa254f5bd4de7d3a0479fbfa3ee996d143ad2832fb055dbdcb7aba294",
                java.util.HexFormat.of().formatHex(sha));
    }

    @Test
    void jsonFormatIsAnArrayOfObjects() {
        assertEquals(0, run("-n", "2", "-s", "3", "-f", "json", "-r", "rus"));
        String t = out.strip();
        assertTrue(t.startsWith("[") && t.endsWith("]"), t);
        // One record per line; a native name's object has its own firstName key.
        assertEquals(2, t.lines().filter(l -> l.startsWith("  {\"firstName\"")).count(), t);
        assertTrue(t.contains("\"region\":\"RUS\""), t);
    }

    @Test
    void jsonStringsAreEscaped() {
        assertEquals("\"a\\\"b\\\\c\\n\"", CliSupport.quote("a\"b\\c\n"));
        assertEquals("\"\\u0001\"", CliSupport.quote("\u0001"));
        assertEquals("\"\\u007f\\u2028\\u2029\"", CliSupport.quote("\u007f\u2028\u2029"));
    }

    @Test
    void badArgumentsExitWithUsage() {
        assertEquals(2, run("--region", "mars"));
        assertTrue(err.contains("region must be"), err);
        assertEquals(2, run("-n"));
        assertEquals(2, run("-n", "-3"));
        assertEquals(2, run("--min-age", "50", "--max-age", "40"));
        assertEquals(2, run("--bogus"));
        assertEquals(2, run("--today", "2026-13-01"));
        assertTrue(err.contains("--today must be a date like 2026-11-20"), err);
        assertEquals(2, run("--today", "tomorrow"));
        assertEquals(2, run("--today"));
        assertTrue(err.contains("usage:"), err);
    }

    @Test
    void helpPrintsUsage() {
        assertEquals(0, run("--help"));
        assertTrue(out.contains("--region"), out);
        assertTrue(out.contains("--data-dir"), out);
    }

    @Test
    void dataDirReplacesTheBundledFiles(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("western_surnames.txt"), "Quill\n");
        assertEquals(0, run("-n", "20", "-s", "4", "-r", "western", "-f", "pipe", "--data-dir", dir.toString()));
        out.lines().skip(1).forEach(l -> assertEquals("Quill", l.split("\\|")[1], l));
    }

    @Test
    void dataDirProblemsExitNonZeroWithTheReason(@TempDir Path dir) throws Exception {
        assertEquals(2, run("--data-dir", dir.resolve("missing").toString()));
        assertTrue(err.contains("not a folder"), err);
        assertEquals(2, run("--data-dir"));
        Files.writeString(dir.resolve("western_firstnames.txt"), "Annika|F\n");
        assertEquals(2, run("--data-dir", dir.toString()));
        assertTrue(err.contains("western_firstnames.txt"), err);
        Files.delete(dir.resolve("western_firstnames.txt"));
        Files.writeString(dir.resolve("western_first_names.txt"), "Annika|X\n");
        assertEquals(1, run("--data-dir", dir.toString()));
        assertTrue(err.contains("western_first_names.txt:1") && !err.contains("usage:"), err);
        assertEquals("", out);
    }
}
