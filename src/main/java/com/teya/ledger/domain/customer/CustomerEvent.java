package com.teya.ledger.domain.customer;

import java.time.Instant;
import java.util.Objects;

/**
 * The set of events that can ever happen to a customer aggregate.
 * Sealed so the compiler enforces exhaustive {@code switch}
 * expressions over the event types in folds and projections.
 */
public sealed interface CustomerEvent {

    /**
     * @return the {@link CustomerId} this event applies to.
     */
    CustomerId customerId();

    /**
     * @return the wall-clock instant the event was recorded.
     */
    Instant occurredAt();

    /**
     * Emitted exactly once per customer when the customer is created.
     *
     * @param customerId the new customer's id.
     * @param name       the customer's display name.
     * @param occurredAt event timestamp.
     */
    record CustomerCreated(
        CustomerId customerId,
        String name,
        Instant occurredAt
    ) implements CustomerEvent {

        /** Compact constructor enforcing non-null + non-blank name. */
        public CustomerCreated {
            Objects.requireNonNull(customerId, "customerId");
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }
}
