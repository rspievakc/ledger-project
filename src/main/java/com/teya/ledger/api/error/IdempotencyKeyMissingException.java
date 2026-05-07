package com.teya.ledger.api.error;

/**
 * Raised when a write endpoint receives no {@code Idempotency-Key}
 * header. Mapped to {@link ErrorCode#IDEMPOTENCY_KEY_REQUIRED}.
 */
public class IdempotencyKeyMissingException extends RuntimeException {
    public IdempotencyKeyMissingException() {
        super("Idempotency-Key header is required for write endpoints");
    }
}
