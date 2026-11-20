package org.tauasa.apps.faux.model;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * A fictitious person.
 *
 * <p>Age is not stored, because a stored age goes stale. Call
 * {@link #ageOn(LocalDate)} with the date you want it for.
 *
 * @param firstName   given name
 * @param lastName    family name
 * @param gender      gender
 * @param region      region the names were drawn from
 * @param dateOfBirth date of birth
 * @param email       email address; a generated one is at the employer's domain
 * @param phone       phone number; a generated one is in the fictional 555-0100 to 555-0199 range
 * @param address     postal address
 * @param employer    employer
 */
public record Person(
        String firstName,
        String lastName,
        Gender gender,
        Region region,
        LocalDate dateOfBirth,
        String email,
        String phone,
        Address address,
        Company employer) {

    /** Rejects {@code null} fields. */
    public Person {
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(lastName, "lastName");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(employer, "employer");
    }

    /**
     * Whole years old on the given date.
     *
     * @param date the date to measure age on
     * @return completed years between {@link #dateOfBirth()} and {@code date}
     * @throws IllegalArgumentException if {@code date} is before the date of birth
     */
    public int ageOn(LocalDate date) {
        Objects.requireNonNull(date, "date");
        if (date.isBefore(dateOfBirth)) {
            throw new IllegalArgumentException("date " + date + " is before date of birth " + dateOfBirth);
        }
        return Period.between(dateOfBirth, date).getYears();
    }
}
