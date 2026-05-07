package com.teya.ledger.application;

import com.teya.ledger.domain.account.Account;
import com.teya.ledger.domain.account.AccountEvent;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.account.AccountStatus;
import com.teya.ledger.domain.customer.Customer;
import com.teya.ledger.domain.error.AccountClosedException;
import com.teya.ledger.domain.error.AccountNotEmptyException;
import com.teya.ledger.domain.error.AccountNotFoundException;
import com.teya.ledger.infrastructure.memory.InMemoryEventStore;
import com.teya.ledger.infrastructure.port.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountServiceTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private final Instant now = Instant.parse("2026-05-06T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private CustomerService customers;
    private AccountService accounts;
    private EventStore store;
    private EventEnvelopeMapper mapper;
    private ProjectionCache cache;

    @BeforeEach
    void setUp() {
        store = new InMemoryEventStore();
        mapper = new EventEnvelopeMapper();
        customers = new CustomerService(store, mapper, clock);
        cache = new ProjectionCache(store, mapper);
        accounts = new AccountService(store, mapper, cache, new LockRegistry(), customers, clock);
    }

    @Test
    void open_creates_account_with_zero_balance() {
        Customer alice = customers.create("Alice");
        Account a = accounts.open(alice.id(), GBP, 1_000L);
        assertThat(a.balanceMinorUnits()).isZero();
        assertThat(a.overdraftLimitMinorUnits()).isEqualTo(1_000L);
        assertThat(a.currency()).isEqualTo(GBP);
        assertThat(a.status()).isEqualTo(AccountStatus.OPEN);
    }

    @Test
    void open_rejects_unknown_customer() {
        assertThatThrownBy(() -> accounts.open(
            com.teya.ledger.domain.customer.CustomerId.random(), GBP, 0L))
            .isInstanceOf(com.teya.ledger.domain.error.CustomerNotFoundException.class);
    }

    @Test
    void find_returns_current_state() {
        Customer alice = customers.create("Alice");
        Account opened = accounts.open(alice.id(), GBP, 0L);
        Account fetched = accounts.find(opened.id());
        assertThat(fetched).isEqualTo(opened);
    }

    @Test
    void find_throws_for_unknown_account() {
        assertThatThrownBy(() -> accounts.find(AccountId.random()))
            .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void change_overdraft_emits_event_and_updates_state() {
        Customer alice = customers.create("Alice");
        Account opened = accounts.open(alice.id(), GBP, 0L);
        Account updated = accounts.changeOverdraft(opened.id(), 5_000L);
        assertThat(updated.overdraftLimitMinorUnits()).isEqualTo(5_000L);
    }

    @Test
    void change_overdraft_rejects_closed_account() {
        Customer alice = customers.create("Alice");
        Account opened = accounts.open(alice.id(), GBP, 0L);
        accounts.close(opened.id());
        assertThatThrownBy(() -> accounts.changeOverdraft(opened.id(), 1L))
            .isInstanceOf(AccountClosedException.class);
    }

    @Test
    void close_succeeds_when_balance_is_zero() {
        Customer alice = customers.create("Alice");
        Account opened = accounts.open(alice.id(), GBP, 0L);
        Account closed = accounts.close(opened.id());
        assertThat(closed.status()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void close_rejects_non_zero_balance() {
        Customer alice = customers.create("Alice");
        Account opened = accounts.open(alice.id(), GBP, 0L);
        // Inject a deposit directly (DepositService not yet built; the contract
        // we're testing is purely on AccountService.close).
        store.append("account-" + opened.id(), List.of(
            mapper.toRecord(new AccountEvent.MoneyDeposited(
                opened.id(), 1L, GBP, now.plusSeconds(1), "k"))));
        // Invalidate the AccountService's cache so close()'s find() re-reads
        // from the store. In production this isn't needed because every
        // writer holds the per-account LockRegistry lock and updates the
        // shared cache via cache.apply; the bypass-store-append above is
        // a test-only shortcut while DepositService doesn't exist yet.
        cache.invalidate(opened.id());
        assertThatThrownBy(() -> accounts.close(opened.id()))
            .isInstanceOf(AccountNotEmptyException.class);
    }
}
