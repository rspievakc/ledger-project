package com.teya.ledger.domain.customer;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerTest {

    @Test
    void folds_from_customer_created_event() {
        CustomerId id = CustomerId.random();
        Instant ts = Instant.parse("2026-05-06T10:00:00Z");
        Customer c = Customer.foldFrom(List.of(
            new CustomerEvent.CustomerCreated(id, "Alice", ts)
        ));
        assertThat(c.id()).isEqualTo(id);
        assertThat(c.name()).isEqualTo("Alice");
        assertThat(c.createdAt()).isEqualTo(ts);
    }

    @Test
    void fold_rejects_empty_event_list() {
        assertThatThrownBy(() -> Customer.foldFrom(List.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no events");
    }

    /**
     * The Customer.apply switch is exhaustive over the sealed CustomerEvent
     * hierarchy. As long as CustomerCreated is the only permitted variant,
     * there is no way to "fold a non-CustomerCreated as the first event" —
     * the type system refuses to construct one. This test fails if a new
     * variant is added, which is the right pressure: whoever extends
     * CustomerEvent must come back here and decide whether the new variant
     * is allowed as a first event or whether the fold needs an additional
     * guard.
     */
    @Test
    void sealed_hierarchy_has_only_one_variant_so_first_event_check_is_unnecessary() {
        assertThat(CustomerEvent.class.getPermittedSubclasses())
            .as("CustomerEvent permitted subtypes — extending requires "
                + "revisiting Customer.foldFrom and this test")
            .containsExactly(CustomerEvent.CustomerCreated.class);
    }
}
