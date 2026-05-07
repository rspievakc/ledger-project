package com.teya.ledger.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Stable JSON error envelope. Every 4xx/5xx response uses this shape.
 *
 * @param code      machine-readable error code from {@link ErrorCode}.
 * @param message   human-readable message (English; suitable for logs).
 * @param details   machine-readable context (optional).
 * @param requestId UUID correlation id, echoed for support diagnostics.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String code,
    String message,
    Map<String, Object> details,
    String requestId
) {
}
