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

class FauxNameAndCompanyCliTest {

    private interface Cli {
        int run(String[] args, PrintStream out, PrintStream err);
    }

    private String out;
    private String err;

    private int run(Cli cli, String... args) {
        var o = new ByteArrayOutputStream();
        var e = new ByteArrayOutputStream();
        int code = cli.run(args, new PrintStream(o, true, StandardCharsets.UTF_8),
                new PrintStream(e, true, StandardCharsets.UTF_8));
        out = o.toString(StandardCharsets.UTF_8);
        err = e.toString(StandardCharsets.UTF_8);
        return code;
    }

    @Test
    void namesPrintInEachFormat() {
        assertEquals(0, run(FauxNameCli::run, "-n", "7", "--seed", "1"));
        assertEquals(7, out.lines().count());
        out.lines().forEach(l -> assertTrue(l.matches("\\S.* \\S.*"), l));
        assertTrue(err.contains("seed: 1"), err);

        assertEquals(0, run(FauxNameCli::run, "-n", "5", "-s", "1", "-r", "rus", "-f", "pipe"));
        assertEquals("first|last|native", out.lines().findFirst().orElseThrow());
        assertEquals(6, out.lines().count());
        // Russian names have no native form here, so the last column is empty.
        out.lines().skip(1).forEach(l -> assertEquals(3, l.split("\\|", -1).length, l));
        out.lines().skip(1).forEach(l -> assertTrue(l.endsWith("|"), l));

        assertEquals(0, run(FauxNameCli::run, "-n", "4", "-s", "1", "-r", "rok"));
        out.lines().forEach(l -> assertTrue(l.matches("[A-Za-z' -]+ \\([\\uAC00-\\uD7A3]{2,6}\\)"), l));
        assertEquals(0, run(FauxNameCli::run, "-n", "4", "-s", "1", "-r", "rok", "-f", "pipe"));
        out.lines().skip(1).forEach(l -> assertTrue(l.matches("[^|]+\\|[^|]+\\|[\\uAC00-\\uD7A3]{2,6}"), l));
        assertEquals(0, run(FauxNameCli::run, "-n", "4", "-s", "1", "-r", "rok", "-f", "json"));
        assertEquals(4, out.split("\"nativeName\"").length - 1, out);

        assertEquals(0, run(FauxNameCli::run, "-n", "2", "-s", "1", "-f", "json"));
        assertTrue(out.startsWith("[") && out.strip().endsWith("]"), out);
        // One record per line; a Korean name's nativeName object has its own firstName key.
        assertEquals(2, out.lines().filter(l -> l.startsWith("  {\"firstName\"")).count(), out);
    }

    @Test
    void namesReplayBySeed() {
        run(FauxNameCli::run, "-n", "20", "-s", "5");
        String first = out;
        run(FauxNameCli::run, "-n", "20", "-s", "5");
        assertEquals(first, out);
    }

    @Test
    void nameDataDirReplacesTheBundledFiles(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("western_surnames.txt"), "Quill\n");
        assertEquals(0, run(FauxNameCli::run, "-n", "20", "-s", "4", "-r", "western", "-f", "pipe",
                "--data-dir", dir.toString()));
        out.lines().skip(1).forEach(l -> assertEquals("Quill", l.split("\\|")[1], l));
    }

    @Test
    void companiesAreMarkedUnlessRealCompanies() {
        assertEquals(0, run(FauxCompanyCli::run, "-n", "30", "-s", "2", "-f", "pipe"));
        assertEquals("name|city|state|domain", out.lines().findFirst().orElseThrow());
        out.lines().skip(1).forEach(l -> {
            String[] c = l.split("\\|");
            assertTrue(c[0].endsWith(" [faux]") && c[3].endsWith(".example"), l);
        });
        String marked = out;
        assertEquals(0, run(FauxCompanyCli::run, "-n", "30", "-s", "2", "-f", "pipe", "--real-companies"));
        assertTrue(out.lines().skip(1).noneMatch(l -> l.contains("[faux]")), out);
        assertEquals(marked.lines().count(), out.lines().count());
    }

    @Test
    void companiesPrintAsTextAndJson() {
        assertEquals(0, run(FauxCompanyCli::run, "-n", "3", "-s", "2"));
        assertEquals(3, out.lines().count());
        out.lines().forEach(l -> assertTrue(l.matches(".+ \\[faux\\], .+, [A-Z]{2} \\(.+\\.example\\)"), l));
        assertEquals(0, run(FauxCompanyCli::run, "-n", "2", "-s", "2", "-f", "json"));
        assertEquals(2, out.split("\"domain\"").length - 1);
    }

    @Test
    void badArgumentsExitTwoWithUsage() {
        for (Cli cli : new Cli[] {FauxNameCli::run, FauxCompanyCli::run}) {
            assertEquals(2, run(cli, "-n", "-1"));
            assertEquals(2, run(cli, "--bogus"));
            assertEquals(2, run(cli, "-f", "xml"));
            assertTrue(err.contains("usage:"), err);
            assertEquals(0, run(cli, "--help"));
            assertTrue(out.contains("--data-dir"), out);
        }
        assertEquals(2, run(FauxCompanyCli::run, "-r", "rus"));
        assertEquals(2, run(FauxNameCli::run, "--real-companies"));
        assertEquals(2, run(FauxNameCli::run, "-r", "atlantis"));
    }

    @Test
    void aBadDataFileExitsOne(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("us_companies_domains.txt"), "Acme\n");
        assertEquals(1, run(FauxCompanyCli::run, "--data-dir", dir.toString()));
        assertTrue(err.contains("us_companies_domains.txt:1"), err);
    }
}
