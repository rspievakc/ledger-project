package com.teya.ledger.api.error;

import com.teya.ledger.api.filter.RequestIdFilter;
import com.teya.ledger.domain.error.AccountClosedException;
import com.teya.ledger.domain.error.AccountNotEmptyException;
import com.teya.ledger.domain.error.AccountNotFoundException;
import com.teya.ledger.domain.error.CurrencyMismatchException;
import com.teya.ledger.domain.error.CustomerNotFoundException;
import com.teya.ledger.domain.error.DomainException;
import com.teya.ledger.domain.error.InsufficientFundsException;
import com.teya.ledger.domain.error.InvalidAmountException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps every domain and validation exception to the stable
 * {@link ErrorResponse} envelope. Each response carries the MDC
 * {@code requestId} populated by {@link RequestIdFilter} so the
 * client can quote it back when reporting an issue.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidAmountException.class)
    public ResponseEntity<ErrorResponse> invalidAmount(InvalidAmountException ex) {
        return respond(ErrorCode.INVALID_AMOUNT, ex);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ErrorResponse> currencyMismatch(CurrencyMismatchException ex) {
        return respond(ErrorCode.CURRENCY_MISMATCH, ex);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> insufficientFunds(InsufficientFundsException ex) {
        return respond(ErrorCode.INSUFFICIENT_FUNDS, ex);
    }

    @ExceptionHandler(AccountClosedException.class)
    public ResponseEntity<ErrorResponse> accountClosed(AccountClosedException ex) {
        return respond(ErrorCode.ACCOUNT_CLOSED, ex);
    }

    @ExceptionHandler(AccountNotEmptyException.class)
    public ResponseEntity<ErrorResponse> accountNotEmpty(AccountNotEmptyException ex) {
        return respond(ErrorCode.ACCOUNT_NOT_EMPTY, ex);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> accountNotFound(AccountNotFoundException ex) {
        return respond(ErrorCode.ACCOUNT_NOT_FOUND, ex);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> customerNotFound(CustomerNotFoundException ex) {
        return respond(ErrorCode.CUSTOMER_NOT_FOUND, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> violations = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> {
            Map<String, String> v = new LinkedHashMap<>();
            v.put("field", fe.getField());
            v.put("message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
            violations.add(v);
        });
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        ErrorResponse body = new ErrorResponse(
            code.name(), "request validation failed",
            Map.of("violations", violations),
            MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(code.status()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> illegalArgument(IllegalArgumentException ex) {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        ErrorResponse body = new ErrorResponse(
            code.name(), ex.getMessage(), Map.of(), MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(code.status()).body(body);
    }

    @ExceptionHandler(IdempotencyKeyMissingException.class)
    public ResponseEntity<ErrorResponse> idempotencyKeyMissing(IdempotencyKeyMissingException ex) {
        ErrorCode code = ErrorCode.IDEMPOTENCY_KEY_REQUIRED;
        ErrorResponse body = new ErrorResponse(
            code.name(), ex.getMessage(), Map.of(), MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(code.status()).body(body);
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> idempotencyKeyConflict(IdempotencyKeyConflictException ex) {
        ErrorCode code = ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST;
        ErrorResponse body = new ErrorResponse(
            code.name(), ex.getMessage(), Map.of(),
            MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(code.status()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> uncaught(Exception ex) {
        log.error("uncaught exception", ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        ErrorResponse body = new ErrorResponse(
            code.name(), "internal server error",
            Map.of(), MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(code.status()).body(body);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, DomainException ex) {
        ErrorResponse body = new ErrorResponse(
            code.name(), ex.getMessage(), ex.details(),
            MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(code.status()).body(body);
    }
}
