package com.teya.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /customer}.
 *
 * @param name customer display name; required, 1–200 chars.
 */
@Schema(description = "Body for POST /customer.")
public record CreateCustomerRequest(
    @Schema(description = "Customer display name. Required, 1–200 characters.", example = "Alice")
    @NotBlank @Size(max = 200) String name
) {
}
