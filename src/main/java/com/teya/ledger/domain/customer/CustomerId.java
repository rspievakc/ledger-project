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
     * @param raw a UUID string.
     * @return new {@link CustomerId}.
     */
    public static CustomerId of(String raw) {
        return new CustomerId(UUID.fromString(raw));
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
