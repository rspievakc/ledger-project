package com.teya.ledger.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /account/{id}/deposit} and
 * {@code POST /account/{id}/withdrawal}.
 */
public record MoneyMovementRequest(
    @Positive long amountMinorUnits,
    @NotBlank @Size(min = 3, max = 3) String currency
) {
}
