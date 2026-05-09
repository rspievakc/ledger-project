package com.teya.ledger.domain.customer;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Negative-path tests for {@link Customer} and {@link CustomerEvent.CustomerCreated}
 * compact constructors. The valid paths are covered by {@code CustomerTest}
 * and {@code CustomerServiceTest}; these tests pin the throw branches.
 */
class CustomerValidationTest {

    private final CustomerId id = CustomerId.random();
    private final Instant ts = Instant.parse("2026-05-06T10:00:00Z");

    @Test
    void customer_record_rejects_blank_name() {
        assertThatThrownBy(() -> new Customer(id, "   ", ts))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    @Test
    void customer_created_event_rejects_blank_name() {
        assertThatThrownBy(() -> new CustomerEvent.CustomerCreated(id, "", ts))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }
}
