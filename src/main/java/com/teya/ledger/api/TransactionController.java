package com.teya.ledger.api;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /account/{accountId}/transaction} — cursor-paginated
 * money-movement history for an account.
 */
@RestController
@Tag(name = "Transaction", description = "Read paginated money-movement history for an account")
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
}
