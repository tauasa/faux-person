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
    }

    @Test
    void badArgumentsExitWithUsage() {
        assertEquals(2, run("--region", "mars"));
        assertTrue(err.contains("region must be"), err);
        assertEquals(2, run("-n"));
        assertEquals(2, run("-n", "-3"));
        assertEquals(2, run("--min-age", "50", "--max-age", "40"));
        assertEquals(2, run("--bogus"));
        assertTrue(err.contains("usage:"), err);
    }

    @Test
    void helpPrintsUsage() {
        assertEquals(0, run("--help"));
        assertTrue(out.contains("--region"), out);
    }
}
