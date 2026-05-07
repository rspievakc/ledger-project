package com.teya.ledger.application;

import java.util.UUID;

/**
 * Outcome of a successful {@code DepositService.deposit} call.
 *
 * @param eventId                  unique id of the persisted event.
 * @param seq                      per-stream sequence number assigned by the store.
 * @param balanceAfterMinorUnits   account balance after the deposit was applied.
 */
public record DepositResult(UUID eventId, long seq, long balanceAfterMinorUnits) {
}
