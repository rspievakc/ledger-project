package com.teya.ledger.domain.error;

import com.teya.ledger.domain.account.AccountId;

import java.util.Map;

/**
 * {@code DELETE /account/{id}} attempted while balance is non-zero.
 * Mapped to {@code ACCOUNT_NOT_EMPTY} (HTTP 422).
 */
public final class AccountNotEmptyException extends DomainException {

    public AccountNotEmptyException(AccountId accountId, long balanceMinorUnits) {
        super(
            "account " + accountId + " cannot be closed: balance is " + balanceMinorUnits,
            Map.of(
                "accountId", accountId.toString(),
                "balanceMinorUnits", balanceMinorUnits
            )
        );
    }
}
