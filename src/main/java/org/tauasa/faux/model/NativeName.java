package org.tauasa.faux.model;

import java.util.Objects;

/**
 * A name in its own script, beside the Latin-letter {@link Name} it belongs to:
 * {@code 지훈} and {@code 김} for Ji-hoon Kim.
 *
 * @param firstName given name in the native script
 * @param lastName  family name in the native script
 * @param fullName  the whole name as that language writes it: {@code 김지훈},
 *                  surname first with no space, for Korean
 */
public record NativeName(String firstName, String lastName, String fullName) {

    /**
     * Rejects {@code null} fields.
     *
     * @param firstName given name in the native script
     * @param lastName  family name in the native script
     * @param fullName  the whole name as that language writes it
     * @throws NullPointerException if any field is {@code null}
     */
    public NativeName {
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(lastName, "lastName");
        Objects.requireNonNull(fullName, "fullName");
    }
}
