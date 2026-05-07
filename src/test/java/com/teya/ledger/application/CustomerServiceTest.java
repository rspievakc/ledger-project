package com.teya.ledger.application;

import com.teya.ledger.domain.customer.Customer;
import com.teya.ledger.domain.customer.CustomerId;
import com.teya.ledger.domain.error.CustomerNotFoundException;
import com.teya.ledger.infrastructure.memory.InMemoryEventStore;
import com.teya.ledger.infrastructure.port.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerServiceTest {

    private final Instant fixedNow = Instant.parse("2026-05-06T10:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    private CustomerService service;

    @BeforeEach
    void setUp() {
        EventStore store = new InMemoryEventStore();
        EventEnvelopeMapper mapper = new EventEnvelopeMapper();
        service = new CustomerService(store, mapper, clock);
    }

    @Test
    void create_returns_a_customer_with_assigned_id_and_clock_time() {
        Customer c = service.create("Alice");
        assertThat(c.name()).isEqualTo("Alice");
        assertThat(c.createdAt()).isEqualTo(fixedNow);
        assertThat(c.id()).isNotNull();
    }

    @Test
    void created_customer_can_be_looked_up_by_id() {
        Customer c = service.create("Alice");
        Customer fetched = service.find(c.id());
        assertThat(fetched).isEqualTo(c);
    }

    @Test
    void find_throws_when_customer_does_not_exist() {
        CustomerId unknown = CustomerId.random();
        assertThatThrownBy(() -> service.find(unknown))
            .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void create_rejects_blank_name() {
        assertThatThrownBy(() -> service.create(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
