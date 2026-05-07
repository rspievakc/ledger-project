package com.teya.ledger.domain.account;

import com.teya.ledger.domain.customer.CustomerId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountFoldTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private final AccountId accountId = AccountId.random();
    private final CustomerId customerId = CustomerId.random();
    private final Instant t0 = Instant.parse("2026-05-06T10:00:00Z");

    @Test
    void account_opens_with_zero_balance() {
        Account a = fold(opened(0L));
        assertThat(a.id()).isEqualTo(accountId);
        assertThat(a.customerId()).isEqualTo(customerId);
        assertThat(a.currency()).isEqualTo(GBP);
        assertThat(a.balanceMinorUnits()).isZero();
        assertThat(a.overdraftLimitMinorUnits()).isZero();
        assertThat(a.status()).isEqualTo(AccountStatus.OPEN);
    }

    @Test
    void deposit_increases_balance() {
        Account a = fold(
            opened(0L),
            new AccountEvent.MoneyDeposited(accountId, 5_00L, GBP, t0.plusSeconds(1), "k1")
        );
        assertThat(a.balanceMinorUnits()).isEqualTo(500L);
    }

    @Test
    void withdrawal_decreases_balance() {
        Account a = fold(
            opened(0L),
            new AccountEvent.MoneyDeposited(accountId, 10_00L, GBP, t0.plusSeconds(1), "k1"),
            new AccountEvent.MoneyWithdrawn(accountId, 3_00L, GBP, t0.plusSeconds(2), "k2")
        );
        assertThat(a.balanceMinorUnits()).isEqualTo(700L);
    }

    @Test
    void overdraft_change_updates_limit() {
        Account a = fold(
            opened(0L),
            new AccountEvent.OverdraftLimitChanged(accountId, 50_00L, t0.plusSeconds(1))
        );
        assertThat(a.overdraftLimitMinorUnits()).isEqualTo(5000L);
    }

    @Test
    void close_moves_status_to_closed() {
        Account a = fold(
            opened(0L),
            new AccountEvent.AccountClosed(accountId, t0.plusSeconds(1))
        );
        assertThat(a.status()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void initial_overdraft_persisted_from_open_event() {
        Account a = fold(opened(20_00L));
        assertThat(a.overdraftLimitMinorUnits()).isEqualTo(2000L);
    }

    @Test
    void fold_rejects_empty_list() {
        assertThatThrownBy(() -> Account.foldFrom(List.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no events");
    }

    @Test
    void fold_rejects_first_event_other_than_opened() {
        assertThatThrownBy(() -> Account.foldFrom(List.of(
            new AccountEvent.MoneyDeposited(accountId, 100L, GBP, t0, "k")
        ))).isInstanceOf(IllegalStateException.class)
           .hasMessageContaining("AccountOpened");
    }

    @Test
    void fold_rejects_event_after_closed() {
        assertThatThrownBy(() -> Account.foldFrom(List.of(
            opened(0L),
            new AccountEvent.AccountClosed(accountId, t0.plusSeconds(1)),
            new AccountEvent.MoneyDeposited(accountId, 100L, GBP, t0.plusSeconds(2), "k")
        ))).isInstanceOf(IllegalStateException.class)
           .hasMessageContaining("CLOSED");
    }

    private AccountEvent.AccountOpened opened(long initialOverdraftLimit) {
        return new AccountEvent.AccountOpened(
            accountId, customerId, GBP, initialOverdraftLimit, t0);
    }

    private Account fold(AccountEvent... events) {
        return Account.foldFrom(List.of(events));
    }
}
