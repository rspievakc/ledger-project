package com.teya.ledger.domain.account;

import com.teya.ledger.domain.customer.CustomerId;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Account aggregate, reconstituted by folding {@link AccountEvent}s
 * from the per-account event stream.
 *
 * <p>State (balance, overdraft limit, status) is derived; the source
 * of truth is the event stream. Each application of an event returns
 * a new immutable instance.
 *
 * @param id                          the account's stable identifier.
 * @param customerId                  owning customer.
 * @param currency                    fixed at open time.
 * @param balanceMinorUnits           current balance in minor units.
 * @param overdraftLimitMinorUnits    permitted negative balance, {@code >= 0}.
 * @param status                      lifecycle state.
 * @param openedAt                    when the account was opened.
 * @param lastEventOccurredAt         when the most recent event was recorded.
 */
public record Account(
    AccountId id,
    CustomerId customerId,
    Currency currency,
    long balanceMinorUnits,
    long overdraftLimitMinorUnits,
    AccountStatus status,
    Instant openedAt,
    Instant lastEventOccurredAt
) {

    /** Compact constructor enforcing non-null fields and a non-negative overdraft limit. */
    public Account {
        Objects.requireNonNull(id);
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(status);
        Objects.requireNonNull(openedAt);
        Objects.requireNonNull(lastEventOccurredAt);
        if (overdraftLimitMinorUnits < 0) {
            throw new IllegalArgumentException("overdraftLimitMinorUnits must be >= 0");
        }
    }

    /**
     * Reconstitutes an {@link Account} from its event stream.
     *
     * @param events non-empty, ordered list of events for one account.
     *               First event must be {@link AccountEvent.AccountOpened};
     *               no events are permitted after {@link AccountEvent.AccountClosed}.
     * @return the projected account state.
     * @throws IllegalStateException if the list is empty, the first event is not
     *                               {@link AccountEvent.AccountOpened}, or any event
     *                               follows an {@link AccountEvent.AccountClosed}.
     */
    public static Account foldFrom(List<AccountEvent> events) {
        if (events.isEmpty()) {
            throw new IllegalStateException("cannot fold account from no events");
        }
        Account current = null;
        for (AccountEvent event : events) {
            // The first event initialises the aggregate via the dedicated open() helper;
            // every subsequent event goes through apply() which enforces CLOSED guard.
            current = (current == null) ? open(event) : current.apply(event);
        }
        return current;
    }

    /**
     * Bootstraps the aggregate from the mandatory first {@link AccountEvent.AccountOpened}
     * event. Throws if any other event type is supplied as the first event.
     *
     * @param first the first event in the stream.
     * @return the initial {@link Account} state with zero balance.
     * @throws IllegalStateException if {@code first} is not {@link AccountEvent.AccountOpened}.
     */
    private static Account open(AccountEvent first) {
        if (!(first instanceof AccountEvent.AccountOpened opened)) {
            throw new IllegalStateException(
                "first event must be AccountOpened, got " + first.getClass().getSimpleName());
        }
        return new Account(
            opened.accountId(),
            opened.customerId(),
            opened.currency(),
            0L,
            opened.initialOverdraftLimitMinorUnits(),
            AccountStatus.OPEN,
            opened.occurredAt(),
            opened.occurredAt()
        );
    }

    /**
     * Returns a new {@link Account} with {@code event} applied.
     *
     * <p>The CLOSED guard fires before the switch so it uniformly rejects every
     * event type once the account has reached terminal state.
     *
     * @param event the next event in this account's stream.
     * @return projected state after the event.
     * @throws IllegalStateException if the account is already {@link AccountStatus#CLOSED},
     *                               or if an {@link AccountEvent.AccountOpened} is applied
     *                               to an already-open account (would open it twice).
     */
    public Account apply(AccountEvent event) {
        if (status == AccountStatus.CLOSED) {
            throw new IllegalStateException(
                "cannot apply " + event.getClass().getSimpleName() + " to CLOSED account");
        }
        return switch (event) {
            // An AccountOpened after the first event is always a programming error.
            case AccountEvent.AccountOpened ignored -> throw new IllegalStateException(
                "AccountOpened applied twice");

            // Credit: add to balance using overflow-checked arithmetic.
            case AccountEvent.MoneyDeposited deposited -> withBalance(
                Math.addExact(balanceMinorUnits, deposited.amountMinorUnits()),
                deposited.occurredAt());

            // Debit: subtract from balance using overflow-checked arithmetic.
            case AccountEvent.MoneyWithdrawn withdrawn -> withBalance(
                Math.subtractExact(balanceMinorUnits, withdrawn.amountMinorUnits()),
                withdrawn.occurredAt());

            // Overdraft cap updated; all other fields unchanged.
            case AccountEvent.OverdraftLimitChanged changed -> new Account(
                id, customerId, currency, balanceMinorUnits,
                changed.newLimitMinorUnits(), status, openedAt, changed.occurredAt());

            // Terminal transition: set status to CLOSED.
            case AccountEvent.AccountClosed closed -> new Account(
                id, customerId, currency, balanceMinorUnits, overdraftLimitMinorUnits,
                AccountStatus.CLOSED, openedAt, closed.occurredAt());
        };
    }

    /**
     * Returns a copy of this account with an updated balance and event timestamp.
     *
     * @param newBalance  the new balance in minor units.
     * @param occurredAt  timestamp of the event that caused the balance change.
     * @return new {@link Account} instance.
     */
    private Account withBalance(long newBalance, Instant occurredAt) {
        return new Account(
            id, customerId, currency, newBalance, overdraftLimitMinorUnits,
            status, openedAt, occurredAt);
    }
}
