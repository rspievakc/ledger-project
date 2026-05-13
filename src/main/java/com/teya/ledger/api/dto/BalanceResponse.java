package com.teya.ledger.api.dto;

import java.time.Instant;

/**
 * Response body for {@code GET /account/{id}/balance}.
 *
 * @param balanceMinorUnits balance after folding every money event with
 *                          {@code occurredAt <= asOf}, in minor units.
 * @param asOf              the inclusive cutoff that was applied — echoed
 *                          back so callers can verify what the server
 *                          actually computed (e.g., after timezone or
 *                          precision normalisation).
 */
public record BalanceResponse(long balanceMinorUnits, Instant asOf) {
}
