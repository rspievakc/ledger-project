package com.teya.ledger.domain.error;

import java.util.Map;

/**
 * Deposit or withdrawal amount must be {@code > 0}. Mapped to
 * {@code INVALID_AMOUNT} (HTTP 422).
 */
public final class InvalidAmountException extends DomainException {

    public InvalidAmountException(long amountMinorUnits) {
        super(
            "amount must be > 0; got " + amountMinorUnits,
            Map.of("amountMinorUnits", amountMinorUnits)
        );
    }
}
