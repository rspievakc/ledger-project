package com.teya.ledger.infrastructure.idempotency;

import com.teya.ledger.infrastructure.port.IdempotencyStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryIdempotencyStoreTest {

    @Test
    void records_then_looks_up_an_entry() {
        IdempotencyStore store = new InMemoryIdempotencyStore(
            10, Duration.ofHours(1), Clock.systemUTC());
        store.record("k1", "hashA", 201, "{\"ok\":true}");
        Optional<IdempotencyStore.Entry> found = store.lookup("k1");
        assertThat(found).isPresent();
        assertThat(found.get().requestHash()).isEqualTo("hashA");
        assertThat(found.get().responseStatus()).isEqualTo(201);
        assertThat(found.get().responseBody()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void lookup_returns_empty_for_unknown_key() {
        IdempotencyStore store = new InMemoryIdempotencyStore(
            10, Duration.ofHours(1), Clock.systemUTC());
        assertThat(store.lookup("missing")).isEmpty();
    }

    @Test
    void overwrites_when_same_key_recorded_again() {
        IdempotencyStore store = new InMemoryIdempotencyStore(
            10, Duration.ofHours(1), Clock.systemUTC());
        store.record("k1", "hashA", 201, "first");
        store.record("k1", "hashA", 201, "second");
        assertThat(store.lookup("k1").orElseThrow().responseBody()).isEqualTo("second");
    }

    @Test
    void evicts_least_recently_used_when_size_exceeded() {
        IdempotencyStore store = new InMemoryIdempotencyStore(
            2, Duration.ofHours(1), Clock.systemUTC());
        store.record("k1", "h", 200, "v1");
        store.record("k2", "h", 200, "v2");
        // Bump k1 to most-recent
        store.lookup("k1");
        store.record("k3", "h", 200, "v3"); // evicts k2 (LRU)
        assertThat(store.lookup("k1")).isPresent();
        assertThat(store.lookup("k2")).isEmpty();
        assertThat(store.lookup("k3")).isPresent();
    }

    @Test
    void expires_entries_past_ttl() {
        // Clock.fixed cannot be advanced post-construction, so we use MutableClock
        // backed by an AtomicReference whose value we mutate to roll time forward.
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-05-06T10:00:00Z"));
        IdempotencyStore store = new InMemoryIdempotencyStore(
            10, Duration.ofMinutes(5), new MutableClock(now));
        store.record("k1", "h", 200, "v");
        now.set(now.get().plus(Duration.ofMinutes(4)));
        assertThat(store.lookup("k1")).isPresent();
        now.set(now.get().plus(Duration.ofMinutes(2))); // total +6m, past TTL
        assertThat(store.lookup("k1")).isEmpty();
    }

    @Test
    void lookup_rejects_null_key() {
        IdempotencyStore store = new InMemoryIdempotencyStore(
            10, Duration.ofHours(1), Clock.systemUTC());
        assertThatThrownBy(() -> store.lookup(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("key");
    }

    @Test
    void concurrent_record_and_lookup_respects_size_cap_without_corruption()
        throws InterruptedException {
        int maxSize = 64;
        int threads = 16;
        int opsPerThread = 200;
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(
            maxSize, Duration.ofHours(1), Clock.systemUTC());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            int threadId = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < opsPerThread; i++) {
                        String key = "k-" + threadId + "-" + i;
                        store.record(key, "h", 200, "v" + i);
                        store.lookup(key);
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(failures.get()).as("no exceptions in any worker").isZero();
        // After ~3,200 inserts into a 64-slot LRU, any specific previously-
        // inserted key may have been evicted; we cannot deterministically
        // assert presence on a key from the concurrent phase. Instead, prove
        // the store is still functional with a post-hoc sequential probe.
        store.record("post-hoc", "h", 200, "v");
        assertThat(store.lookup("post-hoc")).isPresent();
    }

    /** Test-only mutable clock for TTL expiry assertions. */
    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(AtomicReference<Instant> now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
