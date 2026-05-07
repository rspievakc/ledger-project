package com.teya.ledger.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /customer/{customerId}/account}.
 *
 * @param currency                    ISO 4217 alpha code (e.g., {@code "GBP"}).
 * @param overdraftLimitMinorUnits    {@code >= 0}, default 0.
 */
public record OpenAccountRequest(
    @NotBlank @Size(min = 3, max = 3) String currency,
    @PositiveOrZero long overdraftLimitMinorUnits
) {
}
