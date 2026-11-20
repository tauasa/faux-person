package org.tauasa.faux.model;

import java.util.Objects;

/**
 * A real US company, used as a generated person's employer and email domain.
 *
 * @param name   company name, for example {@code "Costco"}
 * @param city   headquarters city
 * @param state  headquarters state, two-letter USPS code
 * @param domain primary web domain, for example {@code "costco.com"}
 */
public record Company(String name, String city, String state, String domain) {

    /**
     * Rejects {@code null} fields.
     *
     * @param name   company name, for example {@code "Costco"}
     * @param city   headquarters city
     * @param state  headquarters state, two-letter USPS code
     * @param domain primary web domain, for example {@code "costco.com"}
     * @throws NullPointerException if any field is {@code null}
     */
    public Company {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(domain, "domain");
    }
}
