package com.teya.ledger.application;

import java.time.Instant;
import java.util.Currency;
import java.util.List;

/**
 * One page of an account's transaction history.
 *
 * @param items      the page contents in ascending {@code seq} order.
 * @param nextCursor cursor to pass as {@code after} for the next page,
 *                   or {@code null} if no further records exist.
 */
public record TransactionPage(List<Item> items, Long nextCursor) {

    /** A single money-movement event surfaced to the API. */
    public record Item(
        long seq,
        String type,
        long amountMinorUnits,
        Currency currency,
        Instant occurredAt
    ) {
    }
}
