package com.teya.ledger.application;

import com.teya.ledger.domain.account.Account;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.customer.Customer;
import com.teya.ledger.domain.error.AccountClosedException;
import com.teya.ledger.domain.error.AccountNotFoundException;
import com.teya.ledger.domain.error.CurrencyMismatchException;
import com.teya.ledger.domain.error.InvalidAmountException;
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

class DepositServiceTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency EUR = Currency.getInstance("EUR");
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-06T10:00:00Z"), ZoneOffset.UTC);
    private CustomerService customers;
    private AccountService accounts;
    private DepositService deposits;

    @BeforeEach
    void setUp() {
        EventStore store = new InMemoryEventStore();
        EventEnvelopeMapper mapper = new EventEnvelopeMapper();
        ProjectionCache cache = new ProjectionCache(store, mapper);
        LockRegistry locks = new LockRegistry();
        customers = new CustomerService(store, mapper, clock);
        accounts = new AccountService(store, mapper, cache, locks, customers, clock);
        deposits = new DepositService(store, mapper, cache, locks, accounts, clock);
    }

    @Test
    void deposits_increase_the_balance() {
        Account a = openGbp();
        DepositResult r = deposits.deposit(a.id(), 5_00L, GBP, "k1");
        assertThat(r.balanceAfterMinorUnits()).isEqualTo(500L);
        assertThat(r.seq()).isPositive();
        assertThat(r.eventId()).isNotNull();
    }

    @Test
    void rejects_unknown_account() {
        assertThatThrownBy(() -> deposits.deposit(AccountId.random(), 100L, GBP, "k"))
            .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void rejects_currency_mismatch() {
        Account a = openGbp();
        assertThatThrownBy(() -> deposits.deposit(a.id(), 100L, EUR, "k"))
            .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void rejects_non_positive_amount() {
        Account a = openGbp();
        assertThatThrownBy(() -> deposits.deposit(a.id(), 0L, GBP, "k"))
            .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> deposits.deposit(a.id(), -1L, GBP, "k"))
            .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void rejects_closed_account() {
        Account a = openGbp();
        accounts.close(a.id());
        assertThatThrownBy(() -> deposits.deposit(a.id(), 100L, GBP, "k"))
            .isInstanceOf(AccountClosedException.class);
    }

    private Account openGbp() {
        Customer alice = customers.create("Alice");
        return accounts.open(alice.id(), GBP, 0L);
    }
}
