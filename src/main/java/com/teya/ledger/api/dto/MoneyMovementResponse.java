package com.teya.ledger.api.dto;

/**
 * Response body for both deposit and withdrawal endpoints.
 */
public record MoneyMovementResponse(
    String eventId,
    long seq,
    long balanceAfterMinorUnits
) {
}
