package com.teya.ledger.infrastructure.idempotency;

import com.teya.ledger.infrastructure.port.IdempotencyStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded LRU + TTL implementation of {@link IdempotencyStore}.
 *
 * <p>Entries past {@code ttl} are removed lazily on lookup and
 * eagerly via a full O(n) sweep before each record. Synchronised for
 * simplicity; traffic on this store is at most one access per write
 * request, and {@code maxSize} is expected to be small (low thousands)
 * — see {@code ledger.idempotency.cache-size} in {@code application.yaml}.
 * Both choices keep the lock window cheap.
 */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private final int maxSize;
    private final Duration ttl;
    private final Clock clock;
    private final LinkedHashMap<String, Stored> entries;

    /**
     * Constructs a new store.
     *
     * @param maxSize maximum number of entries to retain (LRU eviction beyond this).
     * @param ttl     time-to-live for each entry; lookups past this age return empty.
     * @param clock   clock used to determine entry age; injectable for testing.
     * @throws IllegalArgumentException if {@code maxSize} is not positive.
     */
    public InMemoryIdempotencyStore(int maxSize, Duration ttl, Clock clock) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0");
        }
        this.maxSize = maxSize;
        this.ttl = Objects.requireNonNull(ttl);
        this.clock = Objects.requireNonNull(clock);
        // accessOrder=true: get() touches the entry and moves it to the tail,
        // so the head is always the least-recently-used candidate for eviction.
        this.entries = new LinkedHashMap<>(16, 0.75f, true);
    }

    /**
     * {@inheritDoc}
     *
     * <p>An expired entry is removed from the store on this call (lazy eviction).
     */
    @Override
    public synchronized Optional<Entry> lookup(String key) {
        Objects.requireNonNull(key, "key");
        Stored s = entries.get(key);
        if (s == null) {
            return Optional.empty();
        }
        if (isExpired(s)) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(s.toEntry());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overwrites any previous entry for the same key — including resetting
     * the TTL clock — and purges expired entries eagerly before insertion.
     * If the store still exceeds {@code maxSize} after purging, the
     * least-recently-used entry is evicted until the size constraint is
     * satisfied.
     *
     * <p>The HTTP idempotency interceptor (Task 5.1) is expected to call
     * {@link #lookup} first; reaching {@code record} for a key that already
     * has a live entry should be unusual in normal traffic. The overwrite
     * semantics here are a defensive fallback rather than the primary path.
     */
    @Override
    public synchronized void record(String key, String requestHash,
                                    int responseStatus, String responseBody) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(requestHash, "requestHash");
        Objects.requireNonNull(responseBody, "responseBody");
        purgeExpired();
        entries.put(key, new Stored(requestHash, responseStatus, responseBody, clock.instant()));
        // Evict least-recently-used entries until the size cap is satisfied.
        // The while shape is defensive; a single record() call adds at most one entry.
        while (entries.size() > maxSize) {
            Iterator<String> it = entries.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    /** Removes all expired entries. Called eagerly before each {@link #record}. */
    private void purgeExpired() {
        Iterator<Map.Entry<String, Stored>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            if (isExpired(it.next().getValue())) {
                it.remove();
            }
        }
    }

    /** Returns {@code true} if the entry's age exceeds the configured TTL. */
    private boolean isExpired(Stored s) {
        return Duration.between(s.recordedAt(), clock.instant()).compareTo(ttl) > 0;
    }

    /**
     * Internal storage record holding the cached response and its recording timestamp.
     *
     * <p>Distinct from the public {@link Entry} type so {@code recordedAt} —
     * which is internal TTL bookkeeping — never escapes the cache boundary
     * to the HTTP layer. {@link #toEntry()} performs the projection.
     *
     * @param requestHash    SHA-256 of the original request.
     * @param responseStatus HTTP status code of the original response.
     * @param responseBody   serialised body of the original response.
     * @param recordedAt     wall-clock instant when this entry was stored.
     */
    private record Stored(String requestHash, int responseStatus,
                          String responseBody, Instant recordedAt) {
        /** Projects this internal record to the public {@link Entry} type. */
        Entry toEntry() {
            return new Entry(requestHash, responseStatus, responseBody);
        }
    }
}
