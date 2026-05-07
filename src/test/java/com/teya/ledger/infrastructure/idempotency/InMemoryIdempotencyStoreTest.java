package com.teya.ledger.infrastructure.idempotency;

import com.teya.ledger.infrastructure.port.IdempotencyStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-05-06T10:00:00Z"));
        Clock clock = Clock.fixed(now.get(), ZoneOffset.UTC);
        // Wrap so we can advance time by allocating a new InMemory store via a clock supplier.
        // Simpler: advance via a mutable clock implementation.
        IdempotencyStore store = new InMemoryIdempotencyStore(
            10, Duration.ofMinutes(5), new MutableClock(now));
        store.record("k1", "h", 200, "v");
        now.set(now.get().plus(Duration.ofMinutes(4)));
        assertThat(store.lookup("k1")).isPresent();
        now.set(now.get().plus(Duration.ofMinutes(2))); // total +6m, past TTL
        assertThat(store.lookup("k1")).isEmpty();
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
