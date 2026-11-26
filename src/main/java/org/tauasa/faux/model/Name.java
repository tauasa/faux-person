package org.tauasa.faux.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A person's name: a first name and a surname, both from one region, in Latin
 * letters, and the same name in its own script where the region's data has it.
 *
 * @param firstName  given name
 * @param lastName   family name, in the form that fits the person's gender
 *                   ({@code "Petrova"} for a woman, {@code "Petrov"} for a man)
 * @param nativeName the name in its own script, such as Hangul for a South
 *                   Korean name, or empty when the region's data has none
 */
public record Name(String firstName, String lastName, Optional<NativeName> nativeName) {

    /**
     * Rejects {@code null} fields.
     *
     * @param firstName  given name
     * @param lastName   family name
     * @param nativeName the name in its own script, or {@link Optional#empty()}
     * @throws NullPointerException if any field is {@code null}
     */
    public Name {
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(lastName, "lastName");
        Objects.requireNonNull(nativeName, "nativeName");
    }

    /**
     * A name with no native-script form.
     *
     * @param firstName given name
     * @param lastName  family name
     * @throws NullPointerException if any field is {@code null}
     */
    public Name(String firstName, String lastName) {
        this(firstName, lastName, Optional.empty());
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
