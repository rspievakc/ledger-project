package com.teya.ledger.domain.error;

import java.util.Map;

/**
 * Base type for every typed domain failure raised by the application
 * layer. The HTTP layer's {@code GlobalExceptionHandler} (Task 4.1)
 * maps each concrete subclass to a stable {@code ErrorCode} and HTTP
 * status code, so the API surface presents a consistent envelope.
 *
 * <p>{@link #details()} carries machine-readable context surfaced to
 * the API client (e.g., {@code accountId}, {@code requestedMinorUnits}).
 * The map is always defensively copied to prevent callers from mutating
 * the recorded context after the fact.
 */
public abstract class DomainException extends RuntimeException {

    private final transient Map<String, Object> details;

    /**
     * @param message human-readable diagnostic; suitable for logs.
     * @param details machine-readable context, copied defensively.
     */
    protected DomainException(String message, Map<String, Object> details) {
        super(message);
        this.details = Map.copyOf(details);
    }

    /**
     * @return immutable view of the structured details for this failure.
     */
    public Map<String, Object> details() {
        return details;
    }
}
