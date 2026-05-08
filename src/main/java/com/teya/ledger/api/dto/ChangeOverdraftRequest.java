package com.teya.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request body for {@code PATCH /account/{accountId}/overdraft-limit}.
 */
@Schema(description = "Body for PATCH /account/{accountId}/overdraft-limit.")
public record ChangeOverdraftRequest(
    @Schema(description = "New overdraft cap in minor units; >= 0.",
        example = "50000")
    @PositiveOrZero long newLimitMinorUnits
) {
}
