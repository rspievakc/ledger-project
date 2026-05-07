package com.teya.ledger.domain.account;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountIdTest {

    @Test
    void wraps_a_uuid() {
        UUID uuid = UUID.randomUUID();
        AccountId id = new AccountId(uuid);
        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    void parses_from_string() {
        UUID uuid = UUID.randomUUID();
        AccountId id = AccountId.of(uuid.toString());
        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> new AccountId(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void random_generates_unique_ids() {
        assertThat(AccountId.random()).isNotEqualTo(AccountId.random());
    }

    @Test
    void to_string_is_uuid_string() {
        UUID uuid = UUID.randomUUID();
        assertThat(new AccountId(uuid)).hasToString(uuid.toString());
    }

    @Test
    void of_rejects_null_string() {
        assertThatThrownBy(() -> AccountId.of(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("raw");
    }

    @Test
    void of_rejects_malformed_string_with_domain_message() {
        assertThatThrownBy(() -> AccountId.of("not-a-uuid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("AccountId")
            .hasMessageContaining("not-a-uuid");
    }
}
