package com.teya.ledger.domain.customer;

import java.util.Objects;
import java.util.UUID;

/**
 * Type-safe identifier for a customer. A thin wrapper around
 * {@link UUID} that prevents accidental mixing with other id types
 * (e.g., {@code AccountId}) at compile time.
 *
 * @param value the underlying UUID; must be non-null.
 */
public record CustomerId(UUID value) {

    /** Compact constructor enforcing non-null. */
    public CustomerId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * Parses a {@link CustomerId} from a UUID string.
     *
     * @param raw a UUID string; must be non-null and well-formed.
     * @return new {@link CustomerId}.
     * @throws NullPointerException     if {@code raw} is null.
     * @throws IllegalArgumentException if {@code raw} is not a valid UUID;
     *                                  the message names the domain type so
     *                                  HTTP error responses surface a useful
     *                                  diagnostic instead of a raw "Invalid
     *                                  UUID string" from the JDK.
     */
    public static CustomerId of(String raw) {
        Objects.requireNonNull(raw, "raw must not be null");
        try {
            return new CustomerId(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid CustomerId: '" + raw + "' is not a valid UUID", e);
        }
    }

    /**
     * @return a freshly-generated random {@link CustomerId}.
     */
    public static CustomerId random() {
        return new CustomerId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
