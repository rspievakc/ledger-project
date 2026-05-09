package com.teya.ledger.domain.account;

import com.teya.ledger.domain.customer.CustomerId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Negative-path tests for {@link AccountEvent} compact constructors.
 * The valid paths are exercised heavily by {@code AccountFoldTest} and
 * {@code AccountServiceTest}; these tests pin the throw branches so a
 * future reader can't accidentally weaken the validation.
 */
class AccountEventValidationTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private final AccountId accountId = AccountId.random();
    private final CustomerId customerId = CustomerId.random();
    private final Instant ts = Instant.parse("2026-05-06T10:00:00Z");

    @Test
    void account_opened_rejects_negative_overdraft() {
        assertThatThrownBy(() -> new AccountEvent.AccountOpened(
            accountId, customerId, GBP, -1L, ts))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("initialOverdraftLimitMinorUnits");
    }

    @Test
    void money_deposited_rejects_zero_amount() {
        assertThatThrownBy(() -> new AccountEvent.MoneyDeposited(
            accountId, 0L, GBP, ts, "k"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amountMinorUnits");
    }

    @Test
    void money_withdrawn_rejects_negative_amount() {
        assertThatThrownBy(() -> new AccountEvent.MoneyWithdrawn(
            accountId, -1L, GBP, ts, "k"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amountMinorUnits");
    }

    @Test
    void overdraft_limit_changed_rejects_negative_limit() {
        assertThatThrownBy(() -> new AccountEvent.OverdraftLimitChanged(
            accountId, -1L, ts))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("newLimitMinorUnits");
    }

    @Test
    void account_constructor_rejects_negative_overdraft_limit() {
        assertThatThrownBy(() -> new Account(
            accountId, customerId, GBP, 0L, -1L, AccountStatus.OPEN, ts, ts))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("overdraftLimitMinorUnits");
    }

    @Test
    void account_apply_rejects_account_opened_twice() {
        AccountEvent.AccountOpened opened = new AccountEvent.AccountOpened(
            accountId, customerId, GBP, 0L, ts);
        Account a = Account.foldFrom(java.util.List.of(opened));
        assertThatThrownBy(() -> a.apply(opened))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AccountOpened applied twice");
    }
}
