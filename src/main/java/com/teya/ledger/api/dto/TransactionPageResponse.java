package com.teya.ledger.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teya.ledger.application.TransactionPage;

import java.time.Instant;
import java.util.List;

/**
 * Response body for {@code GET /account/{id}/transaction}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionPageResponse(List<Item> items, Long nextCursor) {

    public record Item(
        long seq,
        String type,
        long amountMinorUnits,
        String currency,
        Instant occurredAt
    ) {
    }

    public static TransactionPageResponse from(TransactionPage page) {
        List<Item> items = page.items().stream()
            .map(it -> new Item(
                it.seq(),
                it.type(),
                it.amountMinorUnits(),
                it.currency().getCurrencyCode(),
                it.occurredAt()))
            .toList();
        return new TransactionPageResponse(items, page.nextCursor());
    }
}
