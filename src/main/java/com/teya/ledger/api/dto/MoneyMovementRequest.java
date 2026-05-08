package com.teya.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /account/{id}/deposit} and
 * {@code POST /account/{id}/withdrawal}.
 */
@Schema(description = "Body for deposit and withdrawal endpoints.")
public record MoneyMovementRequest(
    @Schema(description = "Amount in the account's currency, in minor units (e.g., pence). Must be > 0.",
        example = "5000")
    @Positive long amountMinorUnits,

    @Schema(description = "ISO 4217 alpha currency code; must match the account's currency.",
        example = "GBP")
    @NotBlank @Size(min = 3, max = 3) String currency
) {
}
