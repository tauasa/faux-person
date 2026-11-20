package org.tauasa.apps.faux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PersonTest {

    private static final Address ADDRESS =
            new Address("1234 Maple Ave", "Springfield", "IL", "62704", "US");

    private static Person bornOn(LocalDate dob) {
        return new Person("Ada", "Lovelace", Gender.FEMALE, dob,
                "ada.lovelace@example.com", "(217) 555-0142", ADDRESS);
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

    @Test
    void recordsRejectNullFields() {
        assertThrows(NullPointerException.class, () -> new Person(
                null, "Lovelace", Gender.FEMALE, LocalDate.of(1990, 1, 1),
                "a.l@example.com", "(217) 555-0142", ADDRESS));
        assertThrows(NullPointerException.class,
                () -> new Address("1 Main St", "Springfield", null, "62704", "US"));
    }
}
