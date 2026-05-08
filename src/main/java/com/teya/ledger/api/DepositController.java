package com.teya.ledger.api;

import com.teya.ledger.api.dto.MoneyMovementRequest;
import com.teya.ledger.api.dto.MoneyMovementResponse;
import com.teya.ledger.api.idempotency.RequiresIdempotency;
import com.teya.ledger.application.DepositResult;
import com.teya.ledger.application.DepositService;
import com.teya.ledger.domain.account.AccountId;
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
 * {@code POST /account/{accountId}/deposit}. The Idempotency-Key
 * contract is enforced by {@link com.teya.ledger.api.idempotency.IdempotencyInterceptor}
 * via the {@link RequiresIdempotency} annotation: missing/blank header
 * → 400, replay with same body → cached response, replay with
 * different body → 409.
 */
@RestController
public class DepositController {

    private final DepositService deposits;

    public DepositController(DepositService deposits) {
        this.deposits = deposits;
    }

    @PostMapping("/account/{accountId}/deposit")
    @RequiresIdempotency
    public ResponseEntity<MoneyMovementResponse> deposit(
        @PathVariable("accountId") String accountId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody MoneyMovementRequest req
    ) {
        DepositResult result = deposits.deposit(
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
