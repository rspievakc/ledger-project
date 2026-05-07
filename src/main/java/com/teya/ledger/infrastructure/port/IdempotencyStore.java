package com.teya.ledger.infrastructure.port;

import java.util.Optional;

/**
 * Bounded cache of idempotency-key → cached-response mappings.
 *
 * <p>Implementations must be thread-safe and must enforce the
 * configured maximum size and TTL.
 *
 * <p>The HTTP layer's idempotency interceptor (Task 5.1) calls
 * {@link #lookup} before each annotated write handler executes; on
 * cache hit with the same {@code requestHash} it replays the cached
 * response, on hit with a different hash it raises a 409, on miss it
 * lets the handler run and then calls {@link #record} with the result.
 */
public interface IdempotencyStore {

    /**
     * Looks up a previously-recorded response by key.
     *
     * @param key idempotency key from the inbound HTTP header.
     * @return the cached entry, or empty if none (or expired).
     */
    Optional<Entry> lookup(String key);

    /**
     * Records a freshly-completed response for replay on future requests
     * with the same key.
     *
     * @param key            idempotency key.
     * @param requestHash    SHA-256 over canonicalised request body + path.
     * @param responseStatus HTTP status code.
     * @param responseBody   serialised response body (JSON string).
     */
    void record(String key, String requestHash, int responseStatus, String responseBody);

    /**
     * Cached entry — returned by {@link #lookup}.
     *
     * @param requestHash    SHA-256 of the original request.
     * @param responseStatus HTTP status code of the original response.
     * @param responseBody   serialised body of the original response.
     */
    record Entry(String requestHash, int responseStatus, String responseBody) {
    }
}
