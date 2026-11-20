package org.tauasa.faux.model;

import java.util.Objects;

/**
 * A US company, used as a generated person's employer and email domain. The
 * generator draws real companies and, by default, marks them: the name ends
 * with {@code " [faux]"} and the domain is under the reserved {@code .example}
 * ({@code "Costco [faux]"}, {@code "costco.example"}).
 *
 * @param name   company name, for example {@code "Costco"} or {@code "Costco [faux]"}
 * @param city   headquarters city
 * @param state  headquarters state, two-letter USPS code
 * @param domain web domain, for example {@code "costco.com"} or {@code "costco.example"}
 */
public record Company(String name, String city, String state, String domain) {

    /**
     * Rejects {@code null} fields.
     *
     * @param name   company name, for example {@code "Costco"}
     * @param city   headquarters city
     * @param state  headquarters state, two-letter USPS code
     * @param domain web domain, for example {@code "costco.com"} or {@code "costco.example"}
     * @throws NullPointerException if any field is {@code null}
     */
    public Company {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(domain, "domain");
    }
}
