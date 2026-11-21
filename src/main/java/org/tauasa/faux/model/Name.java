package org.tauasa.faux.model;

import java.util.Objects;

/**
 * A person's name: a first name and a surname, both from one region.
 *
 * @param firstName given name
 * @param lastName  family name, in the form that fits the person's gender
 *                  ({@code "Petrova"} for a woman, {@code "Petrov"} for a man)
 */
public record Name(String firstName, String lastName) {

    /**
     * Rejects {@code null} fields.
     *
     * @param firstName given name
     * @param lastName  family name
     * @throws NullPointerException if any field is {@code null}
     */
    public Name {
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(lastName, "lastName");
    }

    /**
     * The first name, a space and the surname, for example {@code "Ada Lovelace"}.
     *
     * @return the full name
     */
    public String fullName() {
        return firstName + " " + lastName;
    }
}
