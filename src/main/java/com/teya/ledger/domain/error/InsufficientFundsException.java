package com.teya.ledger.domain.error;

import com.teya.ledger.domain.account.AccountId;

import java.util.Map;

/**
 * Withdrawal would breach {@code -overdraftLimit}. Mapped to
 * {@code INSUFFICIENT_FUNDS} (HTTP 422).
 */
public final class InsufficientFundsException extends DomainException {

    public InsufficientFundsException(AccountId accountId,
                                      long requestedMinorUnits,
                                      long availableMinorUnits) {
        super(
            "withdrawal of " + requestedMinorUnits
                + " exceeds available balance + overdraft (" + availableMinorUnits + ")",
            Map.of(
                "accountId", accountId.toString(),
                "requestedMinorUnits", requestedMinorUnits,
                "availableMinorUnits", availableMinorUnits
            )
        );
    }
}
