package org.tauasa.faux.model;

import java.util.Objects;

/**
 * A postal address. In v1 every address is in the US.
 *
 * @param street     house number and street, for example {@code "1234 Maple Ave"}
 * @param city       city name, for example {@code "Springfield"}
 * @param state      two-letter USPS state code, for example {@code "IL"}
 * @param postalCode five-digit ZIP code, for example {@code "62704"}
 * @param country    ISO 3166-1 alpha-2 country code, {@code "US"} in v1
 */
public record Address(String street, String city, String state, String postalCode, String country) {

    /** Rejects {@code null} fields. */
    public Address {
        Objects.requireNonNull(street, "street");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(postalCode, "postalCode");
        Objects.requireNonNull(country, "country");
    }
}
