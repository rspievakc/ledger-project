package com.teya.ledger.api.idempotency;

import com.teya.ledger.api.error.IdempotencyKeyConflictException;
import com.teya.ledger.api.error.IdempotencyKeyMissingException;
import com.teya.ledger.infrastructure.port.IdempotencyStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Spring MVC {@link HandlerInterceptor} that implements the
 * Idempotency-Key contract for any handler annotated with
 * {@link RequiresIdempotency}.
 *
 * <p>Flow:
 * <ol>
 *   <li>If header missing/blank → throw {@link IdempotencyKeyMissingException}.</li>
 *   <li>Compute {@code requestHash = SHA-256(method + " " + path + " " + body)}.</li>
 *   <li>If cached entry with same hash → write the cached response and short-circuit.</li>
 *   <li>If cached entry with different hash → throw {@link IdempotencyKeyConflictException}.</li>
 *   <li>Otherwise let the handler run; on success record the response body captured by
 *       the {@link ContentCachingResponseWrapper} installed by {@link IdempotencyConfig.BodyCachingFilter}.</li>
 * </ol>
 *
 * <p>The interceptor cooperates with Spring's {@link ContentCachingRequestWrapper} and
 * {@link ContentCachingResponseWrapper}, both set up by
 * {@link IdempotencyConfig.BodyCachingFilter}, so the body is readable here without
 * consuming the controller's own read and without losing the response body that is
 * sent to the client.
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Idempotency-Key";

    /** Request attribute holding the idempotency key for use in {@code afterCompletion}. */
    static final String ATTR_KEY = "idempotency.key";

    /** Request attribute holding the SHA-256 request hash for use in {@code afterCompletion}. */
    static final String ATTR_HASH = "idempotency.hash";

    private final IdempotencyStore store;

    /** Constructs the interceptor with the required idempotency store. */
    public IdempotencyInterceptor(IdempotencyStore store) {
        this.store = store;
    }

    /**
     * Pre-processes each request annotated with {@link RequiresIdempotency}.
     *
     * <p>Validates the {@code Idempotency-Key} header, checks for a cache hit
     * (replaying the cached response if found), detects key reuse with a different
     * body (raising 409), or stores key/hash as request attributes for recording
     * in {@link #afterCompletion}.
     *
     * @return {@code false} (short-circuit) on a cache hit; {@code true} to let
     *         the handler proceed normally.
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)
                || hm.getMethodAnnotation(RequiresIdempotency.class) == null) {
            return true;
        }

        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            throw new IdempotencyKeyMissingException();
        }

        String body = readBody(request);
        String hash = sha256(request.getMethod() + " " + request.getRequestURI() + " " + body);

        Optional<IdempotencyStore.Entry> cached = store.lookup(key);
        if (cached.isPresent()) {
            if (!cached.get().requestHash().equals(hash)) {
                throw new IdempotencyKeyConflictException(key);
            }
            // Cache hit with matching hash — replay the original response.
            response.setStatus(cached.get().responseStatus());
            response.setContentType("application/json");
            response.getWriter().write(cached.get().responseBody());
            return false;
        }

        // Cache miss — stash key and hash so afterCompletion can record the result.
        request.setAttribute(ATTR_KEY, key);
        request.setAttribute(ATTR_HASH, hash);
        return true;
    }

    /**
     * Post-processes each request annotated with {@link RequiresIdempotency}.
     *
     * <p>If the handler completed successfully (no exception, non-error status),
     * reads the response body from the {@link ContentCachingResponseWrapper} that
     * {@link IdempotencyConfig.BodyCachingFilter} inserted into the filter chain,
     * and records it in the idempotency store for future replay.
     *
     * <p>Error responses (4xx/5xx) are intentionally not cached so that clients
     * may correct their input and retry with the same key.
     */
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) throws Exception {
        if (!(handler instanceof HandlerMethod hm)
                || hm.getMethodAnnotation(RequiresIdempotency.class) == null) {
            return;
        }

        Object key = request.getAttribute(ATTR_KEY);
        Object hash = request.getAttribute(ATTR_HASH);
        if (key == null || hash == null) {
            // Either a cache hit (short-circuited) or header was missing — nothing to record.
            return;
        }

        if (ex != null || response.getStatus() >= 400) {
            // Don't cache failure responses — clients should be free to retry
            // with the same key once their input is fixed.
            return;
        }

        // Retrieve the ContentCachingResponseWrapper installed by BodyCachingFilter
        // so we can read the bytes that were already written to the real response.
        ContentCachingResponseWrapper wrapper =
                WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        if (wrapper == null) {
            // Should not happen given the filter is registered, but be defensive.
            return;
        }

        String responseBody = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
        store.record((String) key, (String) hash, response.getStatus(), responseBody);
    }

    /**
     * Reads the request body using the {@link ContentCachingRequestWrapper} buffer
     * when available, falling back to a direct stream read for test environments
     * that bypass the filter chain.
     */
    private static String readBody(HttpServletRequest request) throws java.io.IOException {
        if (request instanceof ContentCachingRequestWrapper c) {
            return new String(c.getContentAsByteArray(), StandardCharsets.UTF_8);
        }
        // Fallback for tests that don't wrap. Reads the stream destructively;
        // tests that exercise replay must send through the filter chain.
        try (var in = request.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Computes a lower-case hex SHA-256 digest of the given input string.
     *
     * @param input the string to hash (UTF-8 encoded).
     * @return 64-character hex string.
     * @throws IllegalStateException if SHA-256 is unavailable (JVM invariant).
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
