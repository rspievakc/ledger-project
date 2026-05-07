package com.teya.ledger.domain.account;

import java.util.Objects;
import java.util.UUID;

/**
 * Type-safe identifier for an account. A thin wrapper around
 * {@link UUID} that prevents accidental mixing with other id types
 * (e.g., {@code CustomerId}) at compile time.
 *
 * @param value the underlying UUID; must be non-null.
 */
public record AccountId(UUID value) {

    /** Compact constructor enforcing non-null. */
    public AccountId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * Parses an {@link AccountId} from a UUID string.
     *
     * @param raw a UUID string; must be non-null and well-formed.
     * @return new {@link AccountId}.
     * @throws NullPointerException     if {@code raw} is null.
     * @throws IllegalArgumentException if {@code raw} is not a valid UUID;
     *                                  the message names the domain type so
     *                                  HTTP error responses surface a useful
     *                                  diagnostic instead of a raw "Invalid
     *                                  UUID string" from the JDK.
     */
    public static AccountId of(String raw) {
        Objects.requireNonNull(raw, "raw must not be null");
        try {
            return new AccountId(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid AccountId: '" + raw + "' is not a valid UUID", e);
        }
    }

    /**
     * @return a freshly-generated random {@link AccountId}.
     */
    public static AccountId random() {
        return new AccountId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
