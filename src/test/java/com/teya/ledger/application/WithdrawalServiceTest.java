package com.teya.ledger.application;

import com.teya.ledger.domain.account.Account;
import com.teya.ledger.domain.customer.Customer;
import com.teya.ledger.domain.error.AccountClosedException;
import com.teya.ledger.domain.error.CurrencyMismatchException;
import com.teya.ledger.domain.error.InsufficientFundsException;
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

class WithdrawalServiceTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency EUR = Currency.getInstance("EUR");
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-06T10:00:00Z"), ZoneOffset.UTC);
    private CustomerService customers;
    private AccountService accounts;
    private DepositService deposits;
    private WithdrawalService withdrawals;

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
    }

    @Test
    void withdrawal_within_balance_succeeds() {
        Account a = openWithBalance(10_00L, 0L);
        WithdrawalResult r = withdrawals.withdraw(a.id(), 3_00L, GBP, "k1");
        assertThat(r.balanceAfterMinorUnits()).isEqualTo(700L);
    }

    @Test
    void withdrawal_within_overdraft_succeeds() {
        Account a = openWithBalance(0L, 5_00L);
        WithdrawalResult r = withdrawals.withdraw(a.id(), 4_00L, GBP, "k1");
        assertThat(r.balanceAfterMinorUnits()).isEqualTo(-400L);
    }

    @Test
    void withdrawal_breaching_overdraft_fails() {
        Account a = openWithBalance(0L, 5_00L);
        assertThatThrownBy(() -> withdrawals.withdraw(a.id(), 5_01L, GBP, "k1"))
            .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void rejects_currency_mismatch() {
        Account a = openWithBalance(10_00L, 0L);
        assertThatThrownBy(() -> withdrawals.withdraw(a.id(), 100L, EUR, "k"))
            .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void rejects_non_positive_amount() {
        Account a = openWithBalance(10_00L, 0L);
        assertThatThrownBy(() -> withdrawals.withdraw(a.id(), 0L, GBP, "k"))
            .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void rejects_closed_account() {
        Account a = openWithBalance(0L, 0L);
        accounts.close(a.id());
        assertThatThrownBy(() -> withdrawals.withdraw(a.id(), 100L, GBP, "k"))
            .isInstanceOf(AccountClosedException.class);
    }

    private Account openWithBalance(long balance, long overdraft) {
        Customer alice = customers.create("Alice");
        Account a = accounts.open(alice.id(), GBP, overdraft);
        if (balance > 0L) {
            deposits.deposit(a.id(), balance, GBP, "init-" + a.id());
        }
        return accounts.find(a.id());
    }
}
