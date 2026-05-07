package com.teya.ledger.api.dto;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request body for {@code PATCH /account/{accountId}/overdraft-limit}.
 */
public record ChangeOverdraftRequest(
    @PositiveOrZero long newLimitMinorUnits
) {
}
