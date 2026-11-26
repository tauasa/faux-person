package org.tauasa.faux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tauasa.faux.model.Name;
import org.tauasa.faux.model.Person;
import org.tauasa.faux.model.Region;

class NameGeneratorTest {

    private static final DataSet DATA = DataSet.get();

    @Test
    void sameSeedGivesSameNames() {
        assertEquals(NameGenerator.builder().seed(42).build().next(1000),
                NameGenerator.builder().seed(42).build().next(1000));
        assertNotEquals(NameGenerator.builder().seed(1).build().next(20),
                NameGenerator.builder().seed(2).build().next(20));
    }

    @Test
    void seedIsReportedForReplay() {
        NameGenerator random = NameGenerator.builder().build();
        List<Name> first = random.next(5);
        assertEquals(first, NameGenerator.builder().seed(random.seed()).build().next(5));
    }

    @Test
    void firstNameMatchesThePersonGeneratorsFirstPerson() {
        // Both draw region, gender and name in the same order, so a seed's first
        // name is its first person's name.
        for (long seed = 0; seed < 50; seed++) {
            Person p = PersonGenerator.builder().seed(seed).build().next();
            assertEquals(p.name(), NameGenerator.builder().seed(seed).build().next(), "seed " + seed);
        }
    }

    @ParameterizedTest
    @EnumSource(Region.class)
    void namesComeFromTheChosenRegion(Region region) {
        Set<String> first = DATA.firstNames.get(region).stream()
                .map(DataSet.FirstName::name).collect(Collectors.toSet());
        Set<String> last = DATA.surnames.get(region).stream()
                .flatMap(s -> Stream.of(s.masculine(), s.feminine())).collect(Collectors.toSet());
        for (Name n : NameGenerator.builder().seed(6).region(region).build().next(500)) {
            assertTrue(first.contains(n.firstName()), n.toString());
            assertTrue(last.contains(n.lastName()), n.toString());
        }
    }

    /**
     * A South Korean name carries its Hangul, from the same lines as its romanized
     * first name and surname, written surname first with no space.
     */
    @Test
    void koreanNamesCarryTheirHangulSurnameFirst() {
        var hangulOf = new java.util.HashMap<String, String>();
        DATA.firstNames.get(Region.ROK).forEach(f -> hangulOf.put("first:" + f.name(), f.nativeName()));
        DATA.surnames.get(Region.ROK).forEach(s -> hangulOf.merge("last:" + s.masculine(), s.nativeName(),
                (a, b) -> a + "/" + b));  // Lee is both 이 and 리
        for (Name n : NameGenerator.builder().seed(11).region(Region.ROK).build().next(500)) {
            var k = n.nativeName().orElseThrow(() -> new AssertionError(n.toString()));
            assertEquals(hangulOf.get("first:" + n.firstName()), k.firstName(), n.toString());
            assertTrue(Set.of(hangulOf.get("last:" + n.lastName()).split("/")).contains(k.lastName()), n.toString());
            assertEquals(k.lastName() + k.firstName(), k.fullName(), n.toString());
        }
    }

    /** Mixed people: only South Koreans have a native name, and their email stays in romanized ASCII. */
    @Test
    void onlyKoreanNamesHaveANativeForm() {
        int koreans = 0;
        for (Person p : PersonGenerator.builder().seed(12).build().next(2000)) {
            assertEquals(p.region() == Region.ROK, p.name().nativeName().isPresent(), p.toString());
            if (p.region() == Region.ROK) {
                koreans++;
                assertTrue(p.email().matches("[a-z]+\\.[a-z]+\\d{0,2}@[a-z0-9.-]+"), p.email());
            }
        }
        assertTrue(koreans > 20, "koreans: " + koreans);
    }

    /** A native name needs both halves: a data file with Hangul first names but plain surnames gives none. */
    @Test
    void aNativeNameNeedsBothHalves(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("rok_surnames.txt"), "Kim|6768\n");
        for (Name n : NameGenerator.builder().seed(14).region(Region.ROK).dataDirectory(dir).build().next(50)) {
            assertEquals("Kim", n.lastName());
            assertTrue(n.nativeName().isEmpty(), n.toString());
        }
    }

    @Test
    void genderWeightsPickTheFormOfTheName() {
        Set<String> women = DATA.firstNames.get(Region.RUS).stream()
                .filter(f -> f.gender() != 'M').map(DataSet.FirstName::name).collect(Collectors.toSet());
        Set<String> feminine = DATA.surnames.get(Region.RUS).stream()
                .map(DataSet.Surname::feminine).collect(Collectors.toSet());
        for (Name n : NameGenerator.builder().seed(3).region(Region.RUS).genderWeights(1, 0, 0).build().next(500)) {
            assertTrue(women.contains(n.firstName()), n.toString());
            assertTrue(feminine.contains(n.lastName()), n.toString());
        }
    }

    @Test
    void splitIsReproducibleAndDiffersFromTheParent() {
        NameGenerator a = NameGenerator.builder().seed(9).build();
        NameGenerator b = NameGenerator.builder().seed(9).build();
        NameGenerator sa = a.split();
        assertEquals(sa.next(50), b.split().next(50));
        assertNotEquals(sa.next(50), a.next(50));
    }

    @Test
    void dataDirectoryReplacesTheNameFiles(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("western_surnames.txt"), "Quill\n");
        for (Name n : NameGenerator.builder().seed(4).region(Region.WESTERN).dataDirectory(dir).build().next(50)) {
            assertEquals("Quill", n.lastName());
        }
    }

    @Test
    void badSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> NameGenerator.builder().genderWeights(0, 0, 0).build());
        assertThrows(IllegalArgumentException.class, () -> NameGenerator.builder().genderWeights(-1, 2, 0).build());
        assertThrows(IllegalArgumentException.class, () -> NameGenerator.builder().build().next(-1));
    }

    @Test
    void quickListsGiveTheCountAskedFor() {
        assertEquals(25, NameGenerator.generateListOfRandomNames(25).size());
        assertEquals(10, NameGenerator.generateListOfRandomNames(10, Region.JPN).size());
        assertEquals(10, NameGenerator.builder().build().stream().limit(10).count());
    }
}
