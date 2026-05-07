package com.teya.ledger.domain.customer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerIdTest {

    @Test
    void wraps_a_uuid() {
        UUID uuid = UUID.randomUUID();
        CustomerId id = new CustomerId(uuid);
        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    void parses_from_string() {
        UUID uuid = UUID.randomUUID();
        CustomerId id = CustomerId.of(uuid.toString());
        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> new CustomerId(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void random_generates_unique_ids() {
        assertThat(CustomerId.random()).isNotEqualTo(CustomerId.random());
    }

    @Test
    void to_string_is_uuid_string() {
        UUID uuid = UUID.randomUUID();
        assertThat(new CustomerId(uuid)).hasToString(uuid.toString());
    }

    @Test
    void of_rejects_null_string() {
        assertThatThrownBy(() -> CustomerId.of(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("raw");
    }

    @Test
    void of_rejects_malformed_string_with_domain_message() {
        assertThatThrownBy(() -> CustomerId.of("not-a-uuid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CustomerId")
            .hasMessageContaining("not-a-uuid");
    }
}
