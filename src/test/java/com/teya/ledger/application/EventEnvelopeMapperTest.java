package com.teya.ledger.application;

import com.teya.ledger.domain.account.AccountEvent;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.customer.CustomerEvent;
import com.teya.ledger.domain.customer.CustomerId;
import com.teya.ledger.infrastructure.port.EventRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeMapperTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private final EventEnvelopeMapper mapper = new EventEnvelopeMapper();
    private final Instant ts = Instant.parse("2026-05-06T10:00:00Z");

    @Test
    void roundtrips_account_opened() {
        AccountId accountId = AccountId.random();
        CustomerId customerId = CustomerId.random();
        AccountEvent.AccountOpened original =
            new AccountEvent.AccountOpened(accountId, customerId, GBP, 1000L, ts);

        EventRecord rec = mapper.toRecord(original);
        assertThat(rec.type()).isEqualTo("AccountOpened");

        AccountEvent decoded = mapper.toAccountEvent(rec);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundtrips_money_deposited() {
        AccountId accountId = AccountId.random();
        AccountEvent.MoneyDeposited original =
            new AccountEvent.MoneyDeposited(accountId, 5_00L, GBP, ts, "k1");

        EventRecord rec = mapper.toRecord(original);
        AccountEvent decoded = mapper.toAccountEvent(rec);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundtrips_money_withdrawn() {
        AccountId accountId = AccountId.random();
        AccountEvent.MoneyWithdrawn original =
            new AccountEvent.MoneyWithdrawn(accountId, 3_00L, GBP, ts, "k2");

        EventRecord rec = mapper.toRecord(original);
        AccountEvent decoded = mapper.toAccountEvent(rec);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundtrips_overdraft_limit_changed() {
        AccountId accountId = AccountId.random();
        AccountEvent.OverdraftLimitChanged original =
            new AccountEvent.OverdraftLimitChanged(accountId, 50_00L, ts);

        EventRecord rec = mapper.toRecord(original);
        AccountEvent decoded = mapper.toAccountEvent(rec);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundtrips_account_closed() {
        AccountId accountId = AccountId.random();
        AccountEvent.AccountClosed original =
            new AccountEvent.AccountClosed(accountId, ts);

        EventRecord rec = mapper.toRecord(original);
        AccountEvent decoded = mapper.toAccountEvent(rec);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundtrips_customer_created() {
        CustomerId customerId = CustomerId.random();
        CustomerEvent.CustomerCreated original =
            new CustomerEvent.CustomerCreated(customerId, "Alice", ts);

        EventRecord rec = mapper.toRecord(original);
        CustomerEvent decoded = mapper.toCustomerEvent(rec);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void to_account_event_throws_on_unknown_type() {
        EventRecord rec = new EventRecord(
            42L, UUID.randomUUID(), "Mystery", ts, Map.of("k", "v"));
        assertThatThrownBy(() -> mapper.toAccountEvent(rec))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Mystery")
            .hasMessageContaining("seq=42");
    }

    @Test
    void to_customer_event_throws_on_unknown_type() {
        EventRecord rec = new EventRecord(
            7L, UUID.randomUUID(), "Mystery", ts, Map.of("k", "v"));
        assertThatThrownBy(() -> mapper.toCustomerEvent(rec))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Mystery")
            .hasMessageContaining("seq=7");
    }

    @Test
    void to_account_event_normalises_integer_amount_to_long() {
        // Pins the ((Number) ...).longValue() contract: a payload arriving
        // with Integer-typed numerics (as SnakeYAML 2.x produces for values
        // that fit in 32 bits when not pre-normalised by YamlEventCodec)
        // must still decode to a long-valued event field.
        AccountId accountId = AccountId.random();
        EventRecord rec = new EventRecord(
            1L, UUID.randomUUID(), "MoneyDeposited", ts,
            Map.of(
                "accountId", accountId.toString(),
                "amountMinorUnits", 5000,             // boxed Integer, not Long
                "currency", "GBP",
                "idempotencyKey", "k1"
            ));
        AccountEvent decoded = mapper.toAccountEvent(rec);
        assertThat(decoded).isInstanceOf(AccountEvent.MoneyDeposited.class);
        AccountEvent.MoneyDeposited deposit = (AccountEvent.MoneyDeposited) decoded;
        assertThat(deposit.amountMinorUnits()).isEqualTo(5000L);
    }
}
