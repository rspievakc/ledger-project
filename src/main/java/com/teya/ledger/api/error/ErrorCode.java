package com.teya.ledger.api.error;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes returned in {@link ErrorResponse#code}. Each
 * value is paired with the HTTP status code surfaced to the client.
 */
public enum ErrorCode {
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_AMOUNT(HttpStatus.UNPROCESSABLE_ENTITY),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_NOT_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND),
    IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
