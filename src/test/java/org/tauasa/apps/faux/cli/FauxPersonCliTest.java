package org.tauasa.apps.faux.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

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
    void pipeFormatHasAHeaderAndThirteenColumns() {
        assertEquals(0, run("-n", "3", "-s", "2", "--format", "pipe"));
        var lines = out.lines().toList();
        assertEquals(4, lines.size());
        lines.forEach(l -> assertEquals(13, l.split("\\|", -1).length, l));
    }

    @Test
    void jsonFormatIsAnArrayOfObjects() {
        assertEquals(0, run("-n", "2", "-s", "3", "-f", "json", "-r", "rus"));
        String t = out.strip();
        assertTrue(t.startsWith("[") && t.endsWith("]"), t);
        assertEquals(2, t.split("\"firstName\"").length - 1);
        assertTrue(t.contains("\"region\":\"RUS\""), t);
    }

    @Test
    void jsonStringsAreEscaped() {
        assertEquals("\"a\\\"b\\\\c\\n\"", FauxPersonCli.quote("a\"b\\c\n"));
        assertEquals("\"\\u0001\"", FauxPersonCli.quote("\u0001"));
        assertEquals("\"\\u007f\\u2028\\u2029\"", FauxPersonCli.quote("\u007f\u2028\u2029"));
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
    }
}
