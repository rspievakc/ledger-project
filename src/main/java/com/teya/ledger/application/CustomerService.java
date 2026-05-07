package com.teya.ledger.application;

import com.teya.ledger.domain.customer.Customer;
import com.teya.ledger.domain.customer.CustomerEvent;
import com.teya.ledger.domain.customer.CustomerId;
import com.teya.ledger.domain.error.CustomerNotFoundException;
import com.teya.ledger.infrastructure.port.EventRecord;
import com.teya.ledger.infrastructure.port.EventStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Application service for customer operations.
 *
 * <p>Customers live in a single shared stream {@code "customers"} so a
 * full lookup folds the entire stream. Acceptable at this scope; a
 * customer-per-stream layout is a future optimisation if customer
 * counts grow.
 */
@Service
public class CustomerService {

    private static final String STREAM = "customers";
    private static final int PAGE_SIZE = 200;

    private final EventStore eventStore;
    private final EventEnvelopeMapper mapper;
    private final Clock clock;

    public CustomerService(EventStore eventStore, EventEnvelopeMapper mapper, Clock clock) {
        this.eventStore = eventStore;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Creates a new customer and appends a {@link CustomerEvent.CustomerCreated}.
     *
     * @param name display name; must be non-blank.
     * @return the newly created customer.
     */
    public Customer create(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        CustomerId id = CustomerId.random();
        CustomerEvent.CustomerCreated event =
            new CustomerEvent.CustomerCreated(id, name, clock.instant());
        eventStore.append(STREAM, List.of(mapper.toRecord(event)));
        return new Customer(id, name, event.occurredAt());
    }

    /**
     * Looks up a customer by id.
     *
     * @param id customer id.
     * @return the customer.
     * @throws CustomerNotFoundException if no customer with that id exists.
     */
    public Customer find(CustomerId id) {
        for (CustomerEvent ev : readAllCustomerEvents()) {
            if (ev instanceof CustomerEvent.CustomerCreated created
                && created.customerId().equals(id)) {
                return new Customer(created.customerId(), created.name(), created.occurredAt());
            }
        }
        throw new CustomerNotFoundException(id);
    }

    private List<CustomerEvent> readAllCustomerEvents() {
        List<CustomerEvent> all = new ArrayList<>();
        long after = 0L;
        while (true) {
            List<EventRecord> page = eventStore.readFrom(STREAM, after, PAGE_SIZE);
            if (page.isEmpty()) break;
            for (EventRecord rec : page) {
                all.add(mapper.toCustomerEvent(rec));
                after = rec.seq();
            }
            if (page.size() < PAGE_SIZE) break;
        }
        return all;
    }
}
