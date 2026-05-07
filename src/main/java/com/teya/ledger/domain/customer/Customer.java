package com.teya.ledger.domain.customer;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Customer aggregate, reconstituted by folding {@link CustomerEvent}s
 * from the customer event stream.
 *
 * <p>Customers are minimal in this scope: created once, then immutable.
 * Records are deliberately immutable; future state changes will return
 * new instances.
 *
 * @param id        the customer's stable identifier.
 * @param name      display name; non-blank.
 * @param createdAt instant the customer was created.
 */
public record Customer(CustomerId id, String name, Instant createdAt) {

    public Customer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Reconstitutes a {@link Customer} from its event stream.
     *
     * @param events ordered, non-empty list of events for one customer.
     * @return the projected customer.
     * @throws IllegalStateException if {@code events} is empty.
     */
    public static Customer foldFrom(List<CustomerEvent> events) {
        if (events.isEmpty()) {
            throw new IllegalStateException("cannot fold customer from no events");
        }
        Customer current = null;
        for (CustomerEvent event : events) {
            current = apply(current, event);
        }
        return Objects.requireNonNull(current,
            "apply returned null while folding customer event stream");
    }

    private static Customer apply(Customer current, CustomerEvent event) {
        return switch (event) {
            case CustomerEvent.CustomerCreated created -> new Customer(
                created.customerId(), created.name(), created.occurredAt());
        };
    }
}
