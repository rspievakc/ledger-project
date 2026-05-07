package com.teya.ledger.domain.account;

import com.teya.ledger.domain.customer.CustomerId;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/**
 * The set of events that can ever happen to an account aggregate.
 * Sealed so the compiler enforces exhaustive {@code switch}
 * expressions over the event types in {@link Account#apply}.
 */
public sealed interface AccountEvent {

    /**
     * @return the {@link AccountId} this event applies to.
     */
    AccountId accountId();

    /**
     * @return the wall-clock instant the event was recorded.
     */
    Instant occurredAt();

    /**
     * Emitted exactly once per account when the account is opened.
     *
     * @param accountId                       new account's id.
     * @param customerId                      owner.
     * @param currency                        fixed for the life of the account.
     * @param initialOverdraftLimitMinorUnits {@code >= 0}.
     * @param occurredAt                      event timestamp.
     */
    record AccountOpened(
        AccountId accountId,
        CustomerId customerId,
        Currency currency,
        long initialOverdraftLimitMinorUnits,
        Instant occurredAt
    ) implements AccountEvent {
        /** Compact constructor enforcing non-null fields and a non-negative initial overdraft limit. */
        public AccountOpened {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(customerId, "customerId");
            Objects.requireNonNull(currency, "currency");
            if (initialOverdraftLimitMinorUnits < 0) {
                throw new IllegalArgumentException(
                    "initialOverdraftLimitMinorUnits must be >= 0");
            }
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /**
     * Emitted on every successful deposit.
     *
     * @param accountId        target account.
     * @param amountMinorUnits {@code > 0} amount credited to the account.
     * @param currency         must match the account's currency.
     * @param occurredAt       event timestamp.
     * @param idempotencyKey   the {@code Idempotency-Key} that produced this deposit.
     */
    record MoneyDeposited(
        AccountId accountId,
        long amountMinorUnits,
        Currency currency,
        Instant occurredAt,
        String idempotencyKey
    ) implements AccountEvent {
        /** Compact constructor enforcing non-null fields and a strictly-positive amount. */
        public MoneyDeposited {
            Objects.requireNonNull(accountId, "accountId");
            if (amountMinorUnits <= 0) {
                throw new IllegalArgumentException("amountMinorUnits must be > 0");
            }
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        }
    }

    /**
     * Emitted on every successful withdrawal.
     *
     * @param accountId        target account.
     * @param amountMinorUnits {@code > 0} amount debited from the account.
     * @param currency         must match the account's currency.
     * @param occurredAt       event timestamp.
     * @param idempotencyKey   the {@code Idempotency-Key} that produced this withdrawal.
     */
    record MoneyWithdrawn(
        AccountId accountId,
        long amountMinorUnits,
        Currency currency,
        Instant occurredAt,
        String idempotencyKey
    ) implements AccountEvent {
        /** Compact constructor enforcing non-null fields and a strictly-positive amount. */
        public MoneyWithdrawn {
            Objects.requireNonNull(accountId, "accountId");
            if (amountMinorUnits <= 0) {
                throw new IllegalArgumentException("amountMinorUnits must be > 0");
            }
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        }
    }

    /**
     * Emitted whenever the account's overdraft limit is changed.
     *
     * @param accountId          target account.
     * @param newLimitMinorUnits {@code >= 0} new overdraft cap.
     * @param occurredAt         event timestamp.
     */
    record OverdraftLimitChanged(
        AccountId accountId,
        long newLimitMinorUnits,
        Instant occurredAt
    ) implements AccountEvent {
        /** Compact constructor enforcing non-null fields and a non-negative new limit. */
        public OverdraftLimitChanged {
            Objects.requireNonNull(accountId, "accountId");
            if (newLimitMinorUnits < 0) {
                throw new IllegalArgumentException(
                    "newLimitMinorUnits must be >= 0");
            }
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /**
     * Emitted exactly once when the account is closed. Subsequent
     * events on the same stream are programming errors.
     *
     * @param accountId  target account.
     * @param occurredAt event timestamp.
     */
    record AccountClosed(
        AccountId accountId,
        Instant occurredAt
    ) implements AccountEvent {
        /** Compact constructor enforcing non-null fields. */
        public AccountClosed {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }
}
