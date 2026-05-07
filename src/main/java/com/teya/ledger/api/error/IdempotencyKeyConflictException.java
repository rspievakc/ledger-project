package com.teya.ledger.api.error;

/**
 * Raised when an {@code Idempotency-Key} is reused with a different
 * request body. Mapped to
 * {@link ErrorCode#IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST}.
 */
public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String key) {
        super("Idempotency-Key '" + key + "' was previously used with a different request body");
    }
}
