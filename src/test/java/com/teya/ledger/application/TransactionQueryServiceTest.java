package com.teya.ledger.application;

import com.teya.ledger.domain.account.Account;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.customer.Customer;
import com.teya.ledger.infrastructure.memory.InMemoryEventStore;
import com.teya.ledger.infrastructure.port.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
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
    void check_balance_to_date() {
        try {
            Account a = openGbp();
            deposits.deposit(a.id(), 100L, GBP, "k1");
            Thread.sleep(1000);
            deposits.deposit(a.id(), 200L, GBP, "k2");
            Thread.sleep(1000);
            deposits.deposit(a.id(), 300L, GBP, "k2");
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

    }
}
