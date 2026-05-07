package com.teya.ledger.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /customer}.
 *
 * @param name customer display name; required, 1–200 chars.
 */
public record CreateCustomerRequest(
    @NotBlank @Size(max = 200) String name
) {
}
