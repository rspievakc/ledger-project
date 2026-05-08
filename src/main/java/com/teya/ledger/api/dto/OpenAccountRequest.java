package com.teya.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /customer/{customerId}/account}.
 *
 * @param currency                    ISO 4217 alpha code (e.g., {@code "GBP"}).
 * @param overdraftLimitMinorUnits    {@code >= 0}, default 0.
 */
@Schema(description = "Body for POST /customer/{customerId}/account.")
public record OpenAccountRequest(
    @Schema(description = "ISO 4217 alpha currency code, fixed for the life of the account.",
        example = "GBP")
    @NotBlank @Size(min = 3, max = 3) String currency,

    @Schema(description = "Permitted negative balance in minor units; >= 0.",
        example = "10000")
    @PositiveOrZero long overdraftLimitMinorUnits
) {
}
