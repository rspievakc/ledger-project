package com.teya.ledger.domain.error;

import com.teya.ledger.domain.account.AccountId;

import java.util.Map;

/**
 * Lookup or operation referenced an unknown account. Mapped to
 * {@code ACCOUNT_NOT_FOUND} (HTTP 404).
 */
public final class AccountNotFoundException extends DomainException {

    public AccountNotFoundException(AccountId accountId) {
        super(
            "account " + accountId + " not found",
            Map.of("accountId", accountId.toString())
        );
    }
}
