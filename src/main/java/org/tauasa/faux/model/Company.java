package org.tauasa.faux.model;

import java.util.Objects;

/**
 * A company, used as a generated person's employer and email domain: one of
 * the US companies or one of several thousand from other countries. The
 * generator draws real companies and, by default, marks them: the name ends
 * with {@code " [faux]"} and the domain is under the reserved {@code .example}
 * ({@code "Costco [faux]"}, {@code "costco.example"}; {@code "Sony Group [faux]"},
 * {@code "sony.example"}).
 *
 * @param name    company name, for example {@code "Costco"} or {@code "Costco [faux]"}
 * @param city    headquarters city
 * @param state   headquarters state, two-letter USPS code, for a US company;
 *                empty for a company in another country
 * @param country headquarters country, ISO 3166-1 alpha-2 code ({@code "US"}, {@code "JP"})
 * @param domain  web domain, for example {@code "costco.com"} or {@code "costco.example"}
 */
public record Company(String name, String city, String state, String country, String domain) {

    /**
     * Rejects {@code null} fields.
     *
     * @param name    company name, for example {@code "Costco"}
     * @param city    headquarters city
     * @param state   headquarters state for a US company, or empty
     * @param country headquarters country, ISO 3166-1 alpha-2 code
     * @param domain  web domain, for example {@code "costco.com"} or {@code "costco.example"}
     * @throws NullPointerException if any field is {@code null}
     */
    public Company {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(domain, "domain");
    }

    /**
     * A US company.
     *
     * @param name   company name
     * @param city   headquarters city
     * @param state  headquarters state, two-letter USPS code
     * @param domain web domain
     * @throws NullPointerException if any field is {@code null}
     */
    public Company(String name, String city, String state, String domain) {
        this(name, city, state, "US", domain);
    }
}
