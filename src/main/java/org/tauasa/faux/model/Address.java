package org.tauasa.faux.model;

import java.util.Objects;

/**
 * A postal address, in the US or another country. A field the data has no
 * value for is empty, never {@code null}.
 *
 * @param street     house number and street, for example {@code "1234 Maple Ave"} or
 *                   {@code "Hauptstraße 12"}; empty where the data has a city but no street
 * @param city       city name, for example {@code "Springfield"}
 * @param state      two-letter USPS state code in the US, for example {@code "IL"}; empty elsewhere
 * @param postalCode ZIP code in the US, for example {@code "62704"}; elsewhere the country's
 *                   postal code, or empty where the data has none
 * @param country    ISO 3166-1 alpha-2 country code, for example {@code "US"} or {@code "FR"}
 */
public record Address(String street, String city, String state, String postalCode, String country) {

    /**
     * Rejects {@code null} fields.
     *
     * @param street     house number and street, or empty
     * @param city       city name
     * @param state      two-letter USPS state code in the US, or empty
     * @param postalCode postal code, or empty
     * @param country    ISO 3166-1 alpha-2 country code
     * @throws NullPointerException if any field is {@code null}
     */
    public Address {
        Objects.requireNonNull(street, "street");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(postalCode, "postalCode");
        Objects.requireNonNull(country, "country");
    }
}
