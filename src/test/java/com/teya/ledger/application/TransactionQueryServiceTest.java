package com.teya.ledger.application;

import com.teya.ledger.domain.account.Account;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.customer.Customer;
import com.teya.ledger.infrastructure.memory.InMemoryEventStore;
import com.teya.ledger.infrastructure.port.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionQueryServiceTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-06T10:00:00Z"), ZoneOffset.UTC);
    private CustomerService customers;
    private AccountService accounts;
    private DepositService deposits;
    private WithdrawalService withdrawals;
    private TransactionQueryService queries;

    @BeforeEach
    void setUp() {
        EventStore store = new InMemoryEventStore();
        EventEnvelopeMapper mapper = new EventEnvelopeMapper();
        ProjectionCache cache = new ProjectionCache(store, mapper);
        LockRegistry locks = new LockRegistry();
        customers = new CustomerService(store, mapper, clock);
        accounts = new AccountService(store, mapper, cache, locks, customers, clock);
        deposits = new DepositService(store, mapper, cache, locks, accounts, clock);
        withdrawals = new WithdrawalService(store, mapper, cache, locks, accounts, clock);
        queries = new TransactionQueryService(store, mapper);
    }

    @Test
    void returns_all_money_events_in_order() {
        Account a = openGbp();
        deposits.deposit(a.id(), 100L, GBP, "k1");
        deposits.deposit(a.id(), 200L, GBP, "k2");
        TransactionPage page = queries.history(a.id(), 0L, 10);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).type()).isEqualTo("MoneyDeposited");
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void filters_out_non_money_events_from_history() {
        Account a = openGbp();
        accounts.changeOverdraft(a.id(), 1_000L);
        TransactionPage page = queries.history(a.id(), 0L, 10);
        assertThat(page.items()).isEmpty();
    }

    @Test
    void cursor_pagination_returns_next_page() {
        Account a = openGbp();
        for (int i = 0; i < 5; i++) {
            deposits.deposit(a.id(), 100L, GBP, "k" + i);
        }
        TransactionPage first = queries.history(a.id(), 0L, 2);
        assertThat(first.items()).hasSize(2);
        assertThat(first.nextCursor()).isNotNull();

        TransactionPage second = queries.history(a.id(), first.nextCursor(), 2);
        assertThat(second.items()).hasSize(2);
        assertThat(second.nextCursor()).isNotNull();

        TransactionPage third = queries.history(a.id(), second.nextCursor(), 2);
        assertThat(third.items()).hasSize(1);
        assertThat(third.nextCursor()).isNull();
    }

    @Test
    void rejects_invalid_limit() {
        Account a = openGbp();
        assertThatThrownBy(() -> queries.history(a.id(), 0L, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queries.history(a.id(), 0L, 201))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknown_account_returns_empty_page() {
        TransactionPage page = queries.history(AccountId.random(), 0L, 10);
        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    private Account openGbp() {
        Customer alice = customers.create("Alice");
        return accounts.open(alice.id(), GBP, 0L);
    }

    @Test
    void computes_balance_at_multiple_points_in_time() {
        Instant t0 = clock.instant();
        Instant t1 = t0.plus(Duration.ofMinutes(1));   // +100 -> 100
        Instant t2 = t0.plus(Duration.ofMinutes(2));   // +200 -> 300
        Instant t3 = t0.plus(Duration.ofMinutes(3));   //  -50 -> 250
        Instant t4 = t0.plus(Duration.ofMinutes(4));   // +300 -> 550
        Instant t5 = t0.plus(Duration.ofMinutes(5));   // -100 -> 450

        Account a = openGbp();
        deposits.deposit(a.id(), 100L, GBP, "d1", t1);
        deposits.deposit(a.id(), 200L, GBP, "d2", t2);
        withdrawals.withdraw(a.id(), 50L, GBP, "w1", t3);
        deposits.deposit(a.id(), 300L, GBP, "d3", t4);
        withdrawals.withdraw(a.id(), 100L, GBP, "w2", t5);

        Duration half = Duration.ofSeconds(30);
        assertThat(queries.balanceAt(a.id(), t0.plus(half))).isZero();
        assertThat(queries.balanceAt(a.id(), t1.plus(half))).isEqualTo(100L);
        assertThat(queries.balanceAt(a.id(), t2.plus(half))).isEqualTo(300L);
        assertThat(queries.balanceAt(a.id(), t3.plus(half))).isEqualTo(250L);
        assertThat(queries.balanceAt(a.id(), t4.plus(half))).isEqualTo(550L);
        assertThat(queries.balanceAt(a.id(), t5.plus(half))).isEqualTo(450L);

        // Boundary: cutoff equal to an event's occurredAt includes it.
        assertThat(queries.balanceAt(a.id(), t2)).isEqualTo(300L);
        // Unknown account is empty.
        assertThat(queries.balanceAt(AccountId.random(), t5)).isZero();
    }
}
