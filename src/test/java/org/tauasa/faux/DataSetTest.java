package org.tauasa.faux;

import org.tauasa.faux.model.Company;
import org.tauasa.faux.model.Region;
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
    void everyFirstNameFileIsWeighted(Region r) {
        var fn = DATA.firstNames.get(r);
        assertTrue(fn.stream().allMatch(f -> f.weight() >= 1), r.toString());
        // Weighted, not uniform: the most common first name outweighs the median one.
        long max = fn.stream().mapToLong(DataSet.FirstName::weight).max().orElseThrow();
        long median = fn.stream().mapToLong(DataSet.FirstName::weight).sorted().skip(fn.size() / 2).findFirst().orElseThrow();
        assertTrue(max >= 5 * median, r + ": max " + max + ", median " + median);
    }

    @Test
    void firstNameLinesParseWithAndWithoutWeights() {
        assertEquals(new DataSet.FirstName("Ana", 'F', 1), DataSet.firstName(new String[] {"Ana", "F"}));
        assertEquals(new DataSet.FirstName("Ji-hoon", 'M', 97), DataSet.firstName(new String[] {"Ji-hoon", "M", "97"}));
        assertEquals(new DataSet.FirstName("Alex", 'U', 1), DataSet.firstName(new String[] {"Alex", ""}));
        assertThrows(IllegalArgumentException.class, () -> DataSet.firstName(new String[] {"Ana", "F", "0"}));
        assertThrows(IllegalArgumentException.class, () -> DataSet.firstName(new String[] {"Ana", "F", "lots"}));
        assertThrows(IllegalArgumentException.class, () -> DataSet.firstName(new String[] {"Ana", "X"}));
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
    void nativeNamesParseAsTheFourthField() {
        assertEquals(new DataSet.FirstName("Ji-hoon", 'M', 97, "지훈"),
                DataSet.firstName(new String[] {"Ji-hoon", "M", "97", "지훈"}));
        assertEquals(new DataSet.Surname("Kim", "Kim", 6768, "김"),
                DataSet.surname(new String[] {"Kim", "", "6768", "김"}));
        assertEquals(new DataSet.Surname("Petrov", "Petrova", 1663, "Петров"),
                DataSet.surname(new String[] {"Petrov", "Petrova", "1663", "Петров"}));
        // Three fields still mean masculine|feminine|weight, with no native form.
        assertEquals(null, DataSet.surname(new String[] {"Petrov", "Petrova", "1663"}).nativeName());
        assertThrows(IllegalArgumentException.class, () -> DataSet.firstName(new String[] {"Ji-hoon", "M", "97", ""}));
        assertThrows(IllegalArgumentException.class, () -> DataSet.surname(new String[] {"Kim", "", "6768", ""}));
        assertThrows(IllegalArgumentException.class, () -> DataSet.surname(new String[] {"Kim", "", "many", "김"}));
    }

    /** South Korea's names are romanized, each with its Hangul form; no other region has a native form yet. */
    @Test
    void koreanNamesCarryTheirHangul() {
        Pattern hangul = Pattern.compile("[\\uAC00-\\uD7A3]{1,3}");
        Pattern roman = Pattern.compile("[A-Z][A-Za-z'-]*[a-z]");
        var first = DATA.firstNames.get(Region.ROK);
        var sur = DATA.surnames.get(Region.ROK);
        first.forEach(f -> assertTrue(roman.matcher(f.name()).matches()
                && f.nativeName() != null && hangul.matcher(f.nativeName()).matches(), f.toString()));
        sur.forEach(s -> assertTrue(roman.matcher(s.masculine()).matches() && s.feminine().equals(s.masculine())
                && s.nativeName() != null && hangul.matcher(s.nativeName()).matches(), s.toString()));
        // One line per Hangul name.
        assertEquals(first.size(), first.stream().map(DataSet.FirstName::nativeName).distinct().count());
        assertEquals(sur.size(), sur.stream().map(DataSet.Surname::nativeName).distinct().count());
    }

    /** Each region's script, and the share of name weight that must have a native spelling. */
    private static final Map<Region, Object[]> SCRIPTS = Map.of(
            Region.ROK, new Object[] {"[\\uAC00-\\uD7A3]+", 100},
            Region.JPN, new Object[] {"[\\u3005\\u3040-\\u30FF\\u4E00-\\u9FFF]+", 99},
            Region.RUS, new Object[] {"[\\u0400-\\u04FF]+(-[\\u0400-\\u04FF]+)*", 85},
            Region.GRC, new Object[] {"[\\u0370-\\u03FF\\u1F00-\\u1FFF]+(-[\\u0370-\\u03FF\\u1F00-\\u1FFF]+)*", 80},
            Region.HEB, new Object[] {"[\\u0590-\\u05FF]+([ '\\u05F3\\u05F4-][\\u0590-\\u05FF]+)*", 85},
            Region.PSE, new Object[] {"[\\u0600-\\u06FF\\u0750-\\u077F]+( [\\u0600-\\u06FF\\u0750-\\u077F]+)*", 90},
            Region.NAF, new Object[] {"[\\u0600-\\u06FF\\u0750-\\u077F]+( [\\u0600-\\u06FF\\u0750-\\u077F]+)*", 60},
            Region.IRN, new Object[] {"[\\u0600-\\u06FF\\u0750-\\u077F]+( [\\u0600-\\u06FF\\u0750-\\u077F]+)*", 40});

    /**
     * Native spellings are in their region's script, cover most of each list's
     * weight (floors a little under the built coverage), and only the regions
     * with a script have any.
     */
    @ParameterizedTest
    @EnumSource(Region.class)
    void nativeNamesAreInTheirRegionsScript(Region r) {
        var first = DATA.firstNames.get(r);
        var sur = DATA.surnames.get(r);
        Object[] script = SCRIPTS.get(r);
        if (script == null) {
            assertTrue(first.stream().allMatch(f -> f.nativeName() == null), r.toString());
            assertTrue(sur.stream().allMatch(s -> s.nativeName() == null && s.nativeFeminine() == null), r.toString());
            return;
        }
        Pattern p = Pattern.compile((String) script[0]);
        first.stream().filter(f -> f.nativeName() != null)
                .forEach(f -> assertTrue(p.matcher(f.nativeName()).matches(), f.toString()));
        sur.forEach(s -> {
            if (s.nativeName() != null) {
                assertTrue(p.matcher(s.nativeName()).matches(), s.toString());
            }
            if (s.nativeFeminine() != null) {
                assertTrue(p.matcher(s.nativeFeminine()).matches(), s.toString());
                assertTrue(s.nativeName() != null, "a feminine native needs a masculine one: " + s);
            }
            if (s.feminine().equals(s.masculine())) {
                assertEquals(s.nativeName(), s.nativeFeminine(), s.toString());
            }
        });
        int floor = (Integer) script[1];
        long fw = first.stream().mapToLong(DataSet.FirstName::weight).sum();
        long fn = first.stream().filter(f -> f.nativeName() != null).mapToLong(DataSet.FirstName::weight).sum();
        long sw = sur.stream().mapToLong(DataSet.Surname::weight).sum();
        long sn = sur.stream().filter(s -> s.nativeName() != null).mapToLong(DataSet.Surname::weight).sum();
        assertTrue(fn * 100 >= fw * floor, r + " first names: " + (100 * fn / fw) + "%");
        assertTrue(sn * 100 >= sw * floor, r + " surnames: " + (100 * sn / sw) + "%");
    }

    /** Russian feminine surnames follow their masculine form: Иванов, Иванова. */
    @Test
    void russianFeminineSurnamesMatchTheirMasculine() {
        DataSet.Surname ivanov = DATA.surnames.get(Region.RUS).stream()
                .filter(s -> s.masculine().equals("Ivanov")).findFirst().orElseThrow();
        assertEquals("Иванов", ivanov.nativeName());
        assertEquals("Иванова", ivanov.nativeFeminine());
        assertEquals(new DataSet.Surname("Petrov", "Petrova", 1663, "Петров", "Петрова"),
                DataSet.surname(new String[] {"Petrov", "Petrova", "1663", "Петров", "Петрова"}));
        // Four fields with a feminine form that differs: no native spelling for it.
        assertEquals(null, DataSet.surname(new String[] {"Angelopoulos", "Angelopoulou", "21", "Αγγελόπουλος"})
                .nativeFeminine());
    }

    /**
     * Every bundled file is clean Unicode: strict UTF-8 with no byte-order mark,
     * NFC-normalized (so "José" is the same string however it was typed), with no
     * control or invisible format characters, no replacement characters left by a bad
     * decode, Unix line endings, and no stray whitespace around fields.
     */
    @Test
    void everyDataFileIsCleanUnicode() throws Exception {
        List<java.nio.file.Path> files = allDataFiles();
        assertTrue(files.size() >= 51, "data files: " + files.size());
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
        try (var in = DataSetTest.class.getResourceAsStream("/org/tauasa/faux/data/" + name)) {
            assertTrue(in != null, name + " missing");
            return entries(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** The data file kept outside the jar until the generator reads it. */
    private static final java.nio.file.Path EXTRA = java.nio.file.Path.of("tools/data/extra");

    private static List<String> extra(String name) throws Exception {
        return entries(java.nio.file.Files.readString(EXTRA.resolve(name)));
    }

    private static List<String> entries(String text) {
        return text.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
    }

    private static List<java.nio.file.Path> allDataFiles() throws Exception {
        java.nio.file.Path bundled = java.nio.file.Path.of(
                DataSetTest.class.getResource("/org/tauasa/faux/data/").toURI());
        List<java.nio.file.Path> files = new java.util.ArrayList<>();
        for (java.nio.file.Path dir : List.of(bundled, EXTRA)) {
            try (var s = java.nio.file.Files.list(dir)) {
                s.filter(p -> p.toString().endsWith(".txt")).sorted().forEach(files::add);
            }
        }
        return files;
    }

    @Test
    void theJarHoldsExactlyTheFilesTheGeneratorReads() throws Exception {
        java.nio.file.Path bundled = java.nio.file.Path.of(
                DataSetTest.class.getResource("/org/tauasa/faux/data/").toURI());
        try (var s = java.nio.file.Files.list(bundled)) {
            assertEquals(new java.util.TreeSet<>(DataSet.fileNames()),
                    s.map(p -> p.getFileName().toString()).collect(Collectors.toCollection(java.util.TreeSet::new)));
        }
    }

    @Test
    void everyDataFileStartsWithAHeader() throws Exception {
        for (java.nio.file.Path p : allDataFiles()) {
            String first = java.nio.file.Files.readString(p).lines().findFirst().orElse("");
            assertTrue(first.matches("# [^;]+\\|[^;]+(; [^;]+)+; built \\d{4}-\\d{2}-\\d{2}"), p + ": " + first);
        }
    }

    @Test
    void everyStateHasAtLeast20Cities() throws Exception {
        List<String[]> cities = extra("us_cities_states_zip.txt").stream().map(l -> l.split("\\|", -1)).toList();
        cities.forEach(c -> assertEquals(3, c.length, String.join("|", c)));
        Map<String, Long> perState = cities.stream().collect(Collectors.groupingBy(c -> c[1], Collectors.counting()));
        assertEquals(STATES, perState.keySet());
        perState.forEach((st, n) -> assertTrue(n >= 20, st + " has " + n));
        cities.forEach(c -> assertTrue(ZIP.matcher(c[2]).matches(), String.join("|", c)));
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
