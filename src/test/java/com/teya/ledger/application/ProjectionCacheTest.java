package com.teya.ledger.application;

import com.teya.ledger.domain.account.Account;
import com.teya.ledger.domain.account.AccountEvent;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.account.AccountStatus;
import com.teya.ledger.domain.customer.CustomerId;
import com.teya.ledger.infrastructure.memory.InMemoryEventStore;
import com.teya.ledger.infrastructure.port.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionCacheTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private final EventEnvelopeMapper mapper = new EventEnvelopeMapper();
    private EventStore store;
    private ProjectionCache cache;
    private final AccountId accountId = AccountId.random();
    private final CustomerId customerId = CustomerId.random();
    private final Instant t0 = Instant.parse("2026-05-06T10:00:00Z");

    @BeforeEach
    void setUp() {
        store = new InMemoryEventStore();
        cache = new ProjectionCache(store, mapper);
    }

    @Test
    void load_returns_empty_for_unknown_account() {
        Optional<Account> a = cache.load(accountId);
        assertThat(a).isEmpty();
    }

    @Test
    void load_folds_events_from_store() {
        appendOpened();
        appendDeposit(5_00L, "k1");
        Account a = cache.load(accountId).orElseThrow();
        assertThat(a.balanceMinorUnits()).isEqualTo(500L);
        assertThat(a.status()).isEqualTo(AccountStatus.OPEN);
    }

    @Test
    void second_load_uses_cached_value_without_re_reading() {
        appendOpened();
        Account first = cache.load(accountId).orElseThrow();
        // Mutate the store directly bypassing apply() — cached value should still
        // reflect the previous projection (we did not invalidate).
        store.append(streamId(), List.of(
            mapper.toRecord(new AccountEvent.MoneyDeposited(
                accountId, 100L, GBP, t0.plusSeconds(5), "k_bypass"))));
        Account cached = cache.load(accountId).orElseThrow();
        assertThat(cached).isEqualTo(first);
        assertThat(cached.balanceMinorUnits()).isZero();
    }

    @Test
    void apply_advances_the_cached_projection() {
        appendOpened();
        cache.load(accountId);
        AccountEvent.MoneyDeposited deposit = new AccountEvent.MoneyDeposited(
            accountId, 250L, GBP, t0.plusSeconds(1), "k1");
        cache.apply(accountId, deposit);
        Account updated = cache.load(accountId).orElseThrow();
        assertThat(updated.balanceMinorUnits()).isEqualTo(250L);
    }

    @Test
    void invalidate_forces_reload_from_store() {
        appendOpened();
        cache.load(accountId);
        store.append(streamId(), List.of(
            mapper.toRecord(new AccountEvent.MoneyDeposited(
                accountId, 700L, GBP, t0.plusSeconds(2), "k2"))));
        cache.invalidate(accountId);
        Account reloaded = cache.load(accountId).orElseThrow();
        assertThat(reloaded.balanceMinorUnits()).isEqualTo(700L);
    }

    private String streamId() {
        return "account-" + accountId;
    }

    private void appendOpened() {
        store.append(streamId(), List.of(
            mapper.toRecord(new AccountEvent.AccountOpened(
                accountId, customerId, GBP, 0L, t0))));
    }

    private void appendDeposit(long amount, String key) {
        store.append(streamId(), List.of(
            mapper.toRecord(new AccountEvent.MoneyDeposited(
                accountId, amount, GBP, t0.plusSeconds(1), key))));
    }
}
