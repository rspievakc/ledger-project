package com.teya.ledger.api;

import com.teya.ledger.api.dto.AccountResponse;
import com.teya.ledger.api.dto.ChangeOverdraftRequest;
import com.teya.ledger.api.dto.OpenAccountRequest;
import com.teya.ledger.application.AccountService;
import com.teya.ledger.domain.account.Account;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.customer.CustomerId;
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

/**
 * HTTP endpoints for the account lifecycle.
 *
 * <p>Note the singular resource segments ({@code /customer},
 * {@code /account}) — see {@code docs/architecture.md §7}.
 */
@RestController
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    /** Open a new account for the given customer. */
    @PostMapping("/customer/{customerId}/account")
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

    /** Get the current state of an account. */
    @GetMapping("/account/{accountId}")
    public AccountResponse find(@PathVariable("accountId") String accountId) {
        return AccountResponse.from(accounts.find(AccountId.of(accountId)));
    }

    /** Change an account's overdraft limit. */
    @PatchMapping("/account/{accountId}/overdraft-limit")
    public AccountResponse changeOverdraft(
        @PathVariable("accountId") String accountId,
        @Valid @RequestBody ChangeOverdraftRequest req
    ) {
        return AccountResponse.from(
            accounts.changeOverdraft(AccountId.of(accountId), req.newLimitMinorUnits()));
    }

    /** Close an account; refuses unless the balance is exactly zero. */
    @DeleteMapping("/account/{accountId}")
    public AccountResponse close(@PathVariable("accountId") String accountId) {
        return AccountResponse.from(accounts.close(AccountId.of(accountId)));
    }
}
