package org.tauasa.apps.faux;

import org.tauasa.apps.faux.model.Company;
import org.tauasa.apps.faux.model.Region;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Checks the bundled files keep the sizes and shapes SPEC.md promises. */
class DataSetTest {

    private static final DataSet DATA = DataSet.get();
    private static final Set<String> STATES = Set.of(("AL AK AZ AR CA CO CT DE FL GA HI ID IL IN IA KS KY LA "
            + "ME MD MA MI MN MS MO MT NE NV NH NJ NM NY NC ND OH OK OR PA RI SC SD TN TX UT VT VA WA WV WI WY")
            .split(" "));
    private static final Pattern ZIP = Pattern.compile("\\d{5}");

    @ParameterizedTest
    @EnumSource(Region.class)
    void eachRegionHasEnoughDistinctNames(Region r) {
        // Floors sit a little under each file's size when built (SPEC.md, section 3), so a
        // rebuild that loses a chunk of a list fails here. South Korea has only about 280
        // surnames in all; Wikidata has fewer Iranian and Palestinian people to draw on.
        // Regions not listed need 1,000 of each.
        Map<Region, int[]> floor = Map.ofEntries(
                Map.entry(Region.ROK, new int[] {1000, 100}), Map.entry(Region.IRN, new int[] {500, 800}),
                Map.entry(Region.HEB, new int[] {800, 1000}), Map.entry(Region.PSE, new int[] {300, 150}),
                Map.entry(Region.PHL, new int[] {600, 350}), Map.entry(Region.VNM, new int[] {90, 130}),
                Map.entry(Region.THA, new int[] {800, 850}), Map.entry(Region.CAF, new int[] {1000, 500}),
                Map.entry(Region.PRT, new int[] {550, 550}), Map.entry(Region.GRC, new int[] {500, 350}));
        int[] min = floor.getOrDefault(r, new int[] {1000, 1000});
        long first = DATA.firstNames.get(r).stream().map(DataSet.FirstName::name).distinct().count();
        long surnames = DATA.surnames.get(r).stream().map(DataSet.Surname::masculine).distinct().count();
        assertTrue(first >= min[0], r + " has " + first + " first names");
        assertTrue(surnames >= min[1], r + " has " + surnames + " surnames");
        // Both genders well represented: at least a quarter of names fit each.
        long women = DATA.firstNames.get(r).stream().filter(f -> f.gender() != 'M').count();
        long men = DATA.firstNames.get(r).stream().filter(f -> f.gender() != 'F').count();
        assertTrue(women * 4 >= first && men * 4 >= first, r + ": women " + women + ", men " + men);
    }

    @ParameterizedTest
    @EnumSource(Region.class)
    void everySurnameFileIsWeighted(Region r) {
        var sn = DATA.surnames.get(r);
        assertTrue(sn.stream().allMatch(s -> s.weight() >= 1), r.toString());
        // Weighted, not uniform: the most common surname outweighs the median one.
        long max = sn.stream().mapToLong(DataSet.Surname::weight).max().orElseThrow();
        long median = sn.stream().mapToLong(DataSet.Surname::weight).sorted().skip(sn.size() / 2).findFirst().orElseThrow();
        assertTrue(max >= 5 * median, r + ": max " + max + ", median " + median);
    }

    @Test
    void surnameLinesParseWithAndWithoutWeights() {
        assertEquals(new DataSet.Surname("Kim", "Kim", 1), DataSet.surname(new String[] {"Kim"}));
        assertEquals(new DataSet.Surname("Kim", "Kim", 6766), DataSet.surname(new String[] {"Kim", "6766"}));
        assertEquals(new DataSet.Surname("Petrov", "Petrova", 1),
                DataSet.surname(new String[] {"Petrov", "Petrova"}));
        assertEquals(new DataSet.Surname("Petrov", "Petrova", 1663),
                DataSet.surname(new String[] {"Petrov", "Petrova", "1663"}));
        assertThrows(IllegalArgumentException.class, () -> DataSet.surname(new String[] {"Kim", "0"}));
    }

    @Test
    void hangulFilesPairKoreanScriptWithItsRomanization() throws Exception {
        Pattern hangul = Pattern.compile("[\\uAC00-\\uD7A3]{1,3}");
        Pattern roman = Pattern.compile("[A-Z][A-Za-z'-]*[a-z]");
        List<String> first = resource("rok_hangul_first_names.txt");
        List<String> sur = resource("rok_hangul_surnames.txt");
        assertTrue(first.size() >= 1000, "first names: " + first.size());
        assertTrue(sur.size() >= 100, "surnames: " + sur.size());
        for (String line : first) {
            String[] f = line.split("\\|", -1);
            assertEquals(3, f.length, line);
            assertTrue(hangul.matcher(f[0]).matches() && roman.matcher(f[1]).matches(), line);
            assertTrue(f[2].matches("[FMU]"), line);
        }
        for (String line : sur) {
            String[] f = line.split("\\|", -1);
            assertEquals(3, f.length, line);
            assertTrue(hangul.matcher(f[0]).matches() && roman.matcher(f[1]).matches(), line);
            assertTrue(Long.parseLong(f[2]) >= 1, line);
        }
        assertEquals(first.size(), first.stream().map(l -> l.split("\\|")[0]).distinct().count());
    }

    /**
     * Every bundled file is clean Unicode: strict UTF-8 with no byte-order mark,
     * NFC-normalized (so "José" is the same string however it was typed), with no
     * control or invisible format characters, no replacement characters left by a bad
     * decode, Unix line endings, and no stray whitespace around fields.
     */
    @Test
    void everyDataFileIsCleanUnicode() throws Exception {
        java.nio.file.Path dir = java.nio.file.Path.of(
                DataSetTest.class.getResource("/org/tauasa/apps/faux/data/").toURI());
        List<java.nio.file.Path> files;
        try (var s = java.nio.file.Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".txt")).sorted().toList();
        }
        assertTrue(files.size() >= 50, "data files: " + files.size());
        for (java.nio.file.Path p : files) {
            String name = p.getFileName().toString();
            byte[] raw = java.nio.file.Files.readAllBytes(p);
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(raw)).toString();
            assertTrue(!text.startsWith("\uFEFF"), name + ": byte-order mark");
            assertTrue(java.text.Normalizer.isNormalized(text, java.text.Normalizer.Form.NFC), name + ": not NFC");
            assertTrue(text.indexOf('\uFFFD') < 0, name + ": replacement character");
            assertTrue(text.indexOf('\r') < 0, name + ": CR line ending");
            text.codePoints().filter(c -> c != '\n').forEach(c -> {
                int type = Character.getType(c);
                assertTrue(type != Character.CONTROL && type != Character.FORMAT,
                        name + ": control or format character U+" + Integer.toHexString(c));
            });
            for (String line : text.split("\n")) {
                for (String field : line.split("\\|", -1)) {
                    assertEquals(field.strip(), field, name + ": whitespace around \"" + field + "\"");
                }
            }
        }
    }

    private static List<String> resource(String name) throws Exception {
        try (var in = DataSetTest.class.getResourceAsStream("/org/tauasa/apps/faux/data/" + name)) {
            assertTrue(in != null, name + " missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList();
        }
    }

    @Test
    void everyStateHasAtLeast20Cities() {
        Map<String, Long> perState = DATA.cities.stream()
                .collect(Collectors.groupingBy(DataSet.City::state, Collectors.counting()));
        assertEquals(STATES, perState.keySet());
        perState.forEach((st, n) -> assertTrue(n >= 20, st + " has " + n));
        DATA.cities.forEach(c -> assertTrue(ZIP.matcher(c.postalCode()).matches(), c.toString()));
    }

    @Test
    void streetsAndCompaniesAreInTheFiftyStates() {
        DATA.streets.forEach(s -> {
            assertTrue(STATES.contains(s.state()), s.toString());
            assertTrue(ZIP.matcher(s.postalCode()).matches(), s.toString());
        });
        DATA.companies.forEach(c -> {
            assertTrue(STATES.contains(c.state()), c.toString());
            assertTrue(c.domain().matches("[a-z0-9-]+(\\.[a-z0-9-]+)+"), c.toString());
        });
        assertTrue(DATA.companies.size() >= 1000);
        assertEquals(DATA.companies.size(), DATA.companies.stream().map(Company::domain).distinct().count());
    }
}
