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
     * @param raw a UUID string.
     * @return new {@link AccountId}.
     */
    public static AccountId of(String raw) {
        return new AccountId(UUID.fromString(raw));
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
