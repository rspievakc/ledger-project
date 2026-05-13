package com.teya.ledger.api;

import com.teya.ledger.api.dto.AccountResponse;
import com.teya.ledger.api.dto.ChangeOverdraftRequest;
import com.teya.ledger.api.dto.OpenAccountRequest;
import com.teya.ledger.api.error.ErrorResponse;
import com.teya.ledger.api.idempotency.RequiresIdempotency;
import com.teya.ledger.application.AccountService;
import com.teya.ledger.domain.account.Account;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.customer.CustomerId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Currency;
import java.util.List;

/**
 * HTTP endpoints for the account lifecycle.
 *
 * <p>Note the singular resource segments ({@code /customer},
 * {@code /account}) — see {@code docs/architecture.md §7}.
 */
@RestController
@Tag(name = "Account", description = "Account lifecycle: open, view, change overdraft, close")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    /** Open a new account for the given customer. */
    @PostMapping("/customer/{customerId}/account")
    @RequiresIdempotency
    @Operation(summary = "Open a new account", description = "Account starts at zero balance. Requires Idempotency-Key header.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account opened"),
        @ApiResponse(responseCode = "400", description = "Missing Idempotency-Key or invalid body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Unknown customerId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AccountResponse> open(
        @PathVariable("customerId") String customerId,
        @Valid @RequestBody OpenAccountRequest req
    ) {
        Account opened = accounts.open(
            CustomerId.of(customerId),
            Currency.getInstance(req.currency()),
            req.overdraftLimitMinorUnits());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(AccountResponse.from(opened));
    }

    /** List every account owned by a customer. */
    @GetMapping("/customer/{customerId}/account")
    @Operation(summary = "List a customer's accounts",
        description = "Returns every account owned by the customer, including closed ones "
            + "(distinguish via the `status` field). Unpaginated.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account list"),
        @ApiResponse(responseCode = "404", description = "Unknown customerId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<AccountResponse> listForCustomer(@PathVariable("customerId") String customerId) {
        return accounts.listByCustomer(CustomerId.of(customerId)).stream()
            .map(AccountResponse::from)
            .toList();
    }

    /** Get the current state of an account. */
    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get the current state of an account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account state"),
        @ApiResponse(responseCode = "404", description = "Unknown accountId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AccountResponse find(@PathVariable("accountId") String accountId) {
        return AccountResponse.from(accounts.find(AccountId.of(accountId)));
    }

    /** Change an account's overdraft limit. */
    @PatchMapping("/account/{accountId}/overdraft-limit")
    @RequiresIdempotency
    @Operation(summary = "Change overdraft limit", description = "Requires Idempotency-Key header.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Overdraft updated"),
        @ApiResponse(responseCode = "404", description = "Unknown accountId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Account is closed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AccountResponse changeOverdraft(
        @PathVariable("accountId") String accountId,
        @Valid @RequestBody ChangeOverdraftRequest req
    ) {
        return AccountResponse.from(
            accounts.changeOverdraft(AccountId.of(accountId), req.newLimitMinorUnits()));
    }

    /** Close an account; refuses unless the balance is exactly zero. */
    @DeleteMapping("/account/{accountId}")
    @RequiresIdempotency
    @Operation(summary = "Close an account", description = "Refuses if balance is non-zero. Requires Idempotency-Key header.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account closed"),
        @ApiResponse(responseCode = "404", description = "Unknown accountId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Account balance is non-zero (ACCOUNT_NOT_EMPTY)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AccountResponse close(@PathVariable("accountId") String accountId) {
        return AccountResponse.from(accounts.close(AccountId.of(accountId)));
    }
}
