package com.teya.ledger.api;

import com.teya.ledger.api.dto.BalanceResponse;
import com.teya.ledger.api.dto.TransactionPageResponse;
import com.teya.ledger.api.error.ErrorResponse;
import com.teya.ledger.application.TransactionPage;
import com.teya.ledger.application.TransactionQueryService;
import com.teya.ledger.domain.account.AccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * {@code GET /account/{accountId}/transaction} — cursor-paginated
 * money-movement history for an account, plus
 * {@code GET /account/{accountId}/balance?at=…} — point-in-time balance.
 */
@RestController
@Tag(name = "Transaction", description = "Read paginated money-movement history and point-in-time balance for an account")
public class TransactionController {

    private final TransactionQueryService queries;

    public TransactionController(TransactionQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/account/{accountId}/transaction")
    @Operation(summary = "List money-movement history for an account",
        description = "Cursor-paginated. nextCursor is null when no further records exist. "
            + "Lifecycle events (account opened/closed, overdraft changed) are not surfaced here.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of transactions"),
        @ApiResponse(responseCode = "400", description = "Invalid limit (must be in [1, 200])",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TransactionPageResponse history(
        @PathVariable("accountId") String accountId,
        @RequestParam(value = "after", defaultValue = "0") long after,
        @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        TransactionPage page = queries.history(AccountId.of(accountId), after, limit);
        return TransactionPageResponse.from(page);
    }

    @GetMapping("/account/{accountId}/balance")
    @Operation(summary = "Point-in-time balance for an account",
        description = "Returns the balance after applying every money event with "
            + "occurredAt <= at. The cutoff is inclusive. "
            + "Unknown accounts return 0 (consistent with empty history). "
            + "Lifecycle events are ignored — they don't move money.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Balance at the requested instant"),
        @ApiResponse(responseCode = "400", description = "Missing or malformed 'at' parameter",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BalanceResponse balanceAt(
        @PathVariable("accountId") String accountId,
        @RequestParam("at")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at
    ) {
        long balance = queries.balanceAt(AccountId.of(accountId), at);
        return new BalanceResponse(balance, at);
    }
}
