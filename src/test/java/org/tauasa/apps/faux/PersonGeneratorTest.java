package org.tauasa.apps.faux;

import org.tauasa.apps.faux.model.Address;
import org.tauasa.apps.faux.model.Company;
import org.tauasa.apps.faux.model.Gender;
import org.tauasa.apps.faux.model.Person;
import org.tauasa.apps.faux.model.Region;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PersonGeneratorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 1);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final DataSet DATA = DataSet.get();

    private static PersonGenerator.Builder gen(long seed) {
        return PersonGenerator.builder().seed(seed).clock(CLOCK);
    }

    @Test
    void sameSeedGivesSamePeople() {
        assertEquals(gen(42).build().next(1000), gen(42).build().next(1000));
    }

    @Test
    void differentSeedsGiveDifferentPeople() {
        assertNotEquals(gen(1).build().next(20), gen(2).build().next(20));
    }

    @Test
    void seedIsReportedForReplay() {
        PersonGenerator random = PersonGenerator.builder().clock(CLOCK).build();
        List<Person> first = random.next(5);
        assertEquals(first, gen(random.seed()).build().next(5));
    }

    @Test
    void ageStaysInRangeAtBothEdges() {
        for (Person p : gen(7).ageRange(30, 30).build().next(2000)) {
            assertEquals(30, p.ageOn(TODAY), p.dateOfBirth().toString());
        }
        for (Person p : gen(8).ageRange(0, 120).build().next(2000)) {
            int age = p.ageOn(TODAY);
            assertTrue(age >= 0 && age <= 120, "age " + age);
        }
    }

    @Test
    void emailIsNameAtEmployersDomain() {
        Set<String> domains = DATA.companies.stream().map(Company::domain).collect(Collectors.toSet());
        Pattern shape = Pattern.compile("^[a-z0-9]+\\.[a-z0-9]+\\d{0,2}@[a-z0-9.-]+$");
        for (Person p : gen(3).build().next(2000)) {
            String e = p.email();
            assertTrue(shape.matcher(e).matches(), e);
            assertTrue(e.endsWith("@" + p.employer().domain()), e);
            assertTrue(domains.contains(p.employer().domain()), e);
        }
    }

    @Test
    void phoneIsInTheFictionalRange() {
        Pattern fictional = Pattern.compile("^\\([2-9]\\d{2}\\) 555-01\\d{2}$");
        for (Person p : gen(4).build().next(2000)) {
            assertTrue(fictional.matcher(p.phone()).matches(), p.phone());
            assertNotEquals("11", p.phone().substring(2, 4), p.phone());
        }
    }

    @Test
    void addressIsARealStreetLineWithAHouseNumber() {
        Set<String> lines = DATA.streets.stream()
                .map(s -> s.street() + "|" + s.city() + "|" + s.state() + "|" + s.postalCode())
                .collect(Collectors.toSet());
        for (Person p : gen(5).build().next(2000)) {
            Address a = p.address();
            String[] parts = a.street().split(" ", 2);
            int number = Integer.parseInt(parts[0]);
            assertTrue(number >= 1 && number <= 9999, a.street());
            assertTrue(lines.contains(parts[1] + "|" + a.city() + "|" + a.state() + "|" + a.postalCode()), a.toString());
            assertEquals("US", a.country());
        }
    }

    @ParameterizedTest
    @EnumSource(Region.class)
    void namesComeFromTheChosenRegionAndFitTheGender(Region region) {
        Set<String> female = names(region, 'F');
        Set<String> male = names(region, 'M');
        Set<String> surnames = DATA.surnames.get(region).stream()
                .flatMap(s -> java.util.stream.Stream.of(s.masculine(), s.feminine())).collect(Collectors.toSet());
        for (Person p : gen(6).region(region).genderWeights(1, 1, 0).build().next(1000)) {
            assertEquals(region, p.region());
            assertTrue(surnames.contains(p.lastName()), p.lastName());
            Set<String> fits = p.gender() == Gender.FEMALE ? female : male;
            assertTrue(fits.contains(p.firstName()), p.gender() + " " + p.firstName());
        }
    }

    @Test
    void womenGetTheFeminineSurnameForm() {
        Set<String> feminine = DATA.surnames.get(Region.RUS).stream()
                .map(DataSet.Surname::feminine).collect(Collectors.toSet());
        Set<String> masculineOnly = DATA.surnames.get(Region.RUS).stream()
                .filter(s -> !s.masculine().equals(s.feminine()))
                .map(DataSet.Surname::masculine).collect(Collectors.toSet());
        for (Person p : gen(9).region(Region.RUS).genderWeights(1, 0, 0).build().next(500)) {
            assertTrue(feminine.contains(p.lastName()), p.lastName());
            assertTrue(!masculineOnly.contains(p.lastName()), p.lastName());
        }
    }

    @Test
    void surnamesAreDrawnInProportionToTheirWeight() {
        var sn = DATA.surnames.get(Region.ROK);
        long total = sn.stream().mapToLong(DataSet.Surname::weight).sum();
        DataSet.Surname kim = sn.stream().filter(s -> s.masculine().equals("Kim")).findFirst().orElseThrow();
        double expected = (double) kim.weight() / total;
        int n = 20000;
        long kims = gen(14).region(Region.ROK).build().next(n).stream()
                .filter(p -> p.lastName().equals("Kim")).count();
        double seen = (double) kims / n;
        // Five standard deviations either side: this fails only if the weights are ignored.
        double sd = Math.sqrt(expected * (1 - expected) / n);
        assertTrue(Math.abs(seen - expected) < 5 * sd, "Kim: expected " + expected + ", saw " + seen);
        assertTrue(seen > 10.0 / sn.size(), "Kim should be far above a uniform 1/" + sn.size());
    }

    @Test
    void mixedRegionUsesEveryRegion() {
        Set<Region> seen = EnumSet.noneOf(Region.class);
        gen(10).build().next(300).forEach(p -> seen.add(p.region()));
        assertEquals(EnumSet.allOf(Region.class), seen);
    }

    @Test
    void genderWeightsAreHonoured() {
        assertTrue(gen(11).genderWeights(1, 0, 0).build().next(500).stream()
                .allMatch(p -> p.gender() == Gender.FEMALE));
        assertTrue(gen(12).genderWeights(0, 0, 1).build().next(500).stream()
                .allMatch(p -> p.gender() == Gender.NONBINARY));
    }

    @Test
    void splitIsReproducibleAndIndependent() {
        PersonGenerator a = gen(13).build();
        PersonGenerator b = gen(13).build();
        List<Person> fromA = a.split().next(50);
        assertEquals(fromA, b.split().next(50));
        assertNotEquals(fromA, gen(13).build().next(50));
    }

    @Test
    void invalidSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> gen(1).ageRange(40, 30).build());
        assertThrows(IllegalArgumentException.class, () -> gen(1).ageRange(-1, 30).build());
        assertThrows(IllegalArgumentException.class, () -> gen(1).ageRange(18, 121).build());
        assertThrows(IllegalArgumentException.class, () -> gen(1).genderWeights(0, 0, 0).build());
        assertThrows(IllegalArgumentException.class, () -> gen(1).genderWeights(-1, 2, 0).build());
        assertThrows(IllegalArgumentException.class, () -> gen(1).build().next(-1));
    }

    private static Set<String> names(Region r, char gender) {
        return DATA.firstNames.get(r).stream()
                .filter(f -> f.gender() == gender || f.gender() == 'U')
                .map(DataSet.FirstName::name).collect(Collectors.toSet());
    }

    @Test
    void generateListOfRandomPeopleGivesTheCountAskedFor() {
        assertEquals(25, PersonGenerator.generateListOfRandomPeople(25).size());
        assertEquals(0, PersonGenerator.generateListOfRandomPeople(0).size());
        assertThrows(IllegalArgumentException.class, () -> PersonGenerator.generateListOfRandomPeople(-1));
    }

    @Test
    void generateListOfRandomPeopleKeepsToTheRegionGiven() {
        List<Person> people = PersonGenerator.generateListOfRandomPeople(50, Region.MEX);
        assertTrue(people.stream().allMatch(p -> p.region() == Region.MEX));
        // null means every region, as it does in the builder.
        Set<Region> mixed = PersonGenerator.generateListOfRandomPeople(500, null).stream()
                .map(Person::region).collect(Collectors.toSet());
        assertTrue(mixed.size() > 1, "null should mix regions: " + mixed);
    }

    @Test
    void americansHaveWesternNames() {
        List<Person> people = PersonGenerator.generateListOfRandomAmericans(40);
        assertEquals(40, people.size());
        assertTrue(people.stream().allMatch(p -> p.region() == Region.WESTERN));
    }

    /** Each region has exactly one generateListOfRandomX(int), and it keeps to that region. */
    @Test
    void everyRegionHasItsOwnConvenienceMethod() throws Exception {
        Map<Region, String> owner = new EnumMap<>(Region.class);
        for (Method m : PersonGenerator.class.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || !m.getName().startsWith("generateListOfRandom")
                    || m.getName().equals("generateListOfRandomPeople")
                    || m.getName().equals("generateListOfRandomPeopleFromAroundTheWorld")) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Person> people = (List<Person>) m.invoke(null, 30);
            assertEquals(30, people.size(), m.getName());
            Set<Region> regions = people.stream().map(Person::region).collect(Collectors.toSet());
            assertEquals(1, regions.size(), m.getName() + " mixed regions: " + regions);
            Region r = regions.iterator().next();
            String before = owner.put(r, m.getName());
            assertEquals(null, before, r + " has two methods: " + before + " and " + m.getName());
        }
        assertEquals(EnumSet.allOf(Region.class), owner.keySet());
    }

    @Test
    void peopleFromAroundTheWorldComeInARandomCountWithinTheBounds() {
        Set<Integer> sizes = new java.util.HashSet<>();
        Set<Region> regions = EnumSet.noneOf(Region.class);
        for (int i = 0; i < 200; i++) {
            List<Person> people = PersonGenerator.generateListOfRandomPeopleFromAroundTheWorld(5, 15);
            assertTrue(people.size() >= 5 && people.size() <= 15, "size " + people.size());
            sizes.add(people.size());
            people.forEach(p -> regions.add(p.region()));
        }
        assertTrue(sizes.size() > 5, "the count should vary: " + sizes);
        assertTrue(regions.size() > 20, "people should come from many regions: " + regions);
        assertEquals(7, PersonGenerator.generateListOfRandomPeopleFromAroundTheWorld(7, 7).size());
        assertEquals(0, PersonGenerator.generateListOfRandomPeopleFromAroundTheWorld(0, 0).size());
        assertThrows(IllegalArgumentException.class,
                () -> PersonGenerator.generateListOfRandomPeopleFromAroundTheWorld(-1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> PersonGenerator.generateListOfRandomPeopleFromAroundTheWorld(4, 3));
    }
}
