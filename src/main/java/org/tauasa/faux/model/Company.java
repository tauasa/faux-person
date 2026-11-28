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
 * <p>The address is the headquarters': its city, its state for a US company
 * (empty elsewhere) and its country. The data has no street or postal code for
 * a headquarters, so those are empty.
 *
 * @param name    company name, for example {@code "Costco"} or {@code "Costco [faux]"}
 * @param address headquarters address, for example Issaquah, WA, US
 * @param domain  web domain, for example {@code "costco.com"} or {@code "costco.example"}
 */
public record Company(String name, Address address, String domain) {

    /**
     * Rejects {@code null} fields.
     *
     * @param name    company name, for example {@code "Costco"}
     * @param address headquarters address
     * @param domain  web domain, for example {@code "costco.com"} or {@code "costco.example"}
     * @throws NullPointerException if any field is {@code null}
     */
    public Company {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(domain, "domain");
    }
}
