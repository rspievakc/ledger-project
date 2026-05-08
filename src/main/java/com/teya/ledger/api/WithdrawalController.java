package com.teya.ledger.api;

import com.teya.ledger.api.dto.MoneyMovementRequest;
import com.teya.ledger.api.dto.MoneyMovementResponse;
import com.teya.ledger.api.error.ErrorResponse;
import com.teya.ledger.api.idempotency.RequiresIdempotency;
import com.teya.ledger.application.WithdrawalResult;
import com.teya.ledger.application.WithdrawalService;
import com.teya.ledger.domain.account.AccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Currency;

/**
 * {@code POST /account/{accountId}/withdrawal}. Same idempotency-key
 * contract as {@link DepositController}, enforced by
 * {@link com.teya.ledger.api.idempotency.IdempotencyInterceptor}.
 */
@RestController
@Tag(name = "Withdrawal", description = "Withdraw money from an account")
public class WithdrawalController {

    private final WithdrawalService withdrawals;

    public WithdrawalController(WithdrawalService withdrawals) {
        this.withdrawals = withdrawals;
    }

    @PostMapping("/account/{accountId}/withdrawal")
    @RequiresIdempotency
    @Operation(summary = "Withdraw money from an account",
        description = "Refused if the post-withdrawal balance would go below the configured "
            + "overdraft limit. Requires Idempotency-Key header.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Withdrawal recorded"),
        @ApiResponse(responseCode = "400", description = "Missing Idempotency-Key or invalid body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Unknown accountId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Idempotency-Key reused with a different body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Insufficient funds, currency mismatch, invalid amount, or account closed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MoneyMovementResponse> withdraw(
        @PathVariable("accountId") String accountId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody MoneyMovementRequest req
    ) {
        WithdrawalResult result = withdrawals.withdraw(
            AccountId.of(accountId),
            req.amountMinorUnits(),
            Currency.getInstance(req.currency()),
            idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new MoneyMovementResponse(
                result.eventId().toString(),
                result.seq(),
                result.balanceAfterMinorUnits()));
    }
}
