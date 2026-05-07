package com.teya.ledger.infrastructure.yaml;

import com.teya.ledger.infrastructure.port.EventRecord;
import com.teya.ledger.infrastructure.port.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class YamlEventStoreConcurrencyTest {

    @TempDir
    Path tempDir;

    private EventStore store;

    @BeforeEach
    void setUp() {
        store = new YamlEventStore(tempDir);
    }

    @Test
    void appends_to_distinct_streams_run_in_parallel_without_interference()
        throws InterruptedException {
        int streamCount = 8;
        int eventsPerStream = 200;
        ExecutorService pool = Executors.newFixedThreadPool(streamCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(streamCount);
        AtomicInteger failures = new AtomicInteger();
        for (int s = 0; s < streamCount; s++) {
            String streamId = "s" + s;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < eventsPerStream; i++) {
                        store.append(streamId, List.of(rec("E" + i)));
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
        assertThat(failures.get()).isZero();
        for (int s = 0; s < streamCount; s++) {
            List<EventRecord> all = store.readFrom("s" + s, 0L, eventsPerStream + 10);
            assertThat(all).hasSize(eventsPerStream);
            for (int i = 0; i < eventsPerStream; i++) {
                assertThat(all.get(i).seq()).isEqualTo(i + 1L);
            }
        }
    }

    @Test
    void concurrent_appends_to_same_stream_serialise_with_dense_seqs()
        throws InterruptedException {
        int threads = 16;
        int eventsPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < eventsPerThread; i++) {
                        store.append("hot", List.of(rec("E")));
                    }
                } catch (Exception ignored) {
                    // recorded by missing events below
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        List<EventRecord> all = store.readFrom("hot", 0L, threads * eventsPerThread + 10);
        assertThat(all).hasSize(threads * eventsPerThread);
        for (int i = 0; i < all.size(); i++) {
            assertThat(all.get(i).seq()).isEqualTo(i + 1L);
        }
    }

    private EventRecord rec(String type) {
        return new EventRecord(
            0L, UUID.randomUUID(), type,
            Instant.parse("2026-05-06T10:00:00Z"),
            Map.of("k", "v"));
    }
}
