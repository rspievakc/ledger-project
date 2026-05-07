package com.teya.ledger.domain.error;

import com.teya.ledger.domain.account.AccountId;

import java.util.Map;

/**
 * Write attempted against a CLOSED account. Mapped to
 * {@code ACCOUNT_CLOSED} (HTTP 422).
 */
public final class AccountClosedException extends DomainException {

    public AccountClosedException(AccountId accountId) {
        super(
            "account " + accountId + " is closed",
            Map.of("accountId", accountId.toString())
        );
    }
}
