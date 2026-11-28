package org.tauasa.faux.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class PersonTest {

    private static final Address ADDRESS =
            new Address("1234 Maple Ave", "Springfield", "IL", "62704", "US");
    private static final Company COMPANY = new Company("Costco", new Address("", "Issaquah", "WA", "", "US"), "costco.com");

    private static Person bornOn(LocalDate dob) {
        return new Person(new Name("Ada", "Lovelace"), Gender.FEMALE, Region.WESTERN, dob,
                "ada.lovelace@costco.com", "(217) 555-0142", ADDRESS, COMPANY);
    }

    @Test
    void aNameWithoutANativeFormHasAnEmptyOne() {
        assertEquals(java.util.Optional.empty(), new Name("Ada", "Lovelace").nativeName());
        assertEquals(new Name("Ada", "Lovelace"), new Name("Ada", "Lovelace", java.util.Optional.empty()));
    }

    @Test
    void nameShortcutsMatchTheName() {
        Person p = bornOn(LocalDate.of(1990, 6, 15));
        assertEquals("Ada", p.firstName());
        assertEquals("Lovelace", p.lastName());
        assertEquals("Ada Lovelace", p.name().fullName());
    }

    @Test
    void ageCountsOnlyCompletedYears() {
        Person p = bornOn(LocalDate.of(1990, 6, 15));
        assertEquals(35, p.ageOn(LocalDate.of(2026, 6, 14)));
        assertEquals(36, p.ageOn(LocalDate.of(2026, 6, 15)));
    }

    @Test
    void leapDayBirthdayTurnsOverOnFirstOfMarchInCommonYears() {
        Person p = bornOn(LocalDate.of(2000, 2, 29));
        assertEquals(24, p.ageOn(LocalDate.of(2025, 2, 28)));
        assertEquals(25, p.ageOn(LocalDate.of(2025, 3, 1)));
    }

    @Test
    void ageBeforeBirthIsRejected() {
        Person p = bornOn(LocalDate.of(1990, 6, 15));
        assertThrows(IllegalArgumentException.class, () -> p.ageOn(LocalDate.of(1990, 6, 14)));
    }

    /** Each case nulls one field, so removing any single null check fails a case. */
    static Stream<Function<Object[], Object>> eachNullField() {
        Stream.Builder<Function<Object[], Object>> b = Stream.builder();
        for (int i = 0; i < 8; i++) {
            int at = i;
            b.add(args -> {
                Object[] a = args.clone();
                a[at] = null;
                return new Person((Name) a[0], (Gender) a[1], (Region) a[2],
                        (LocalDate) a[3], (String) a[4], (String) a[5], (Address) a[6], (Company) a[7]);
            });
        }
        for (int i = 0; i < 2; i++) {
            int at = i;
            b.add(args -> {
                String[] a = {"Ada", "Lovelace"};
                a[at] = null;
                return new Name(a[0], a[1]);
            });
        }
        b.add(args -> new Name("Ji-hoon", "Kim", null));
        for (int i = 0; i < 3; i++) {
            int at = i;
            b.add(args -> {
                String[] a = {"지훈", "김", "김지훈"};
                a[at] = null;
                return new NativeName(a[0], a[1], a[2]);
            });
        }
        for (int i = 0; i < 5; i++) {
            int at = i;
            b.add(args -> {
                String[] a = {"1 Main St", "Springfield", "IL", "62704", "US"};
                a[at] = null;
                return new Address(a[0], a[1], a[2], a[3], a[4]);
            });
        }
        for (int i = 0; i < 3; i++) {
            int at = i;
            b.add(args -> {
                Object[] a = {"Costco", new Address("", "Issaquah", "WA", "", "US"), "costco.com"};
                a[at] = null;
                return new Company((String) a[0], (Address) a[1], (String) a[2]);
            });
        }
        return b.build();
    }

    @ParameterizedTest
    @MethodSource("eachNullField")
    void recordsRejectEveryNullField(Function<Object[], Object> build) {
        Object[] valid = {new Name("Ada", "Lovelace"), Gender.FEMALE, Region.WESTERN, LocalDate.of(1990, 1, 1),
                "a.l@costco.com", "(217) 555-0142", ADDRESS, COMPANY};
        assertThrows(NullPointerException.class, () -> build.apply(valid));
    }
}
