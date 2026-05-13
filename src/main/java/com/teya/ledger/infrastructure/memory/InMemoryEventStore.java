package com.teya.ledger.infrastructure.memory;

import com.teya.ledger.infrastructure.port.AppendResult;
import com.teya.ledger.infrastructure.port.EventRecord;
import com.teya.ledger.infrastructure.port.EventStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Process-local {@link EventStore} backed by per-stream {@link ArrayList}s.
 *
 * <p>Used in unit tests and (optionally) at runtime via
 * {@code ledger.storage.type=in-memory}. Holds events for the lifetime
 * of the process; restarts lose all state.
 *
 * <p>Concurrency: a single {@link ReentrantLock} per stream guards all
 * access — both writes and reads. Different streams run in parallel.
 * Holding the same lock across {@code append} and {@code readFrom}
 * makes the no-torn-read guarantee in the {@link EventStore} contract
 * trivially apparent at the call site, without depending on the
 * synchronisation semantics of any wrapper collection.
 */
public final class InMemoryEventStore implements EventStore {

    private final Map<String, List<EventRecord>> streams = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public AppendResult append(String streamId, List<EventRecord> events) {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("events must not be empty");
        }
        ReentrantLock lock = locks.computeIfAbsent(streamId, k -> new ReentrantLock());
        lock.lock();
        try {
            List<EventRecord> stream = streams.computeIfAbsent(
                streamId, k -> new ArrayList<>());
            long firstSeq = stream.size() + 1L;
            List<EventRecord> assigned = new ArrayList<>(events.size());
            for (int i = 0; i < events.size(); i++) {
                EventRecord src = events.get(i);
                assigned.add(new EventRecord(
                    firstSeq + i, src.eventId(), src.type(),
                    src.occurredAt(), src.payload()));
            }
            stream.addAll(assigned);
            return new AppendResult(firstSeq, firstSeq + assigned.size() - 1L, assigned);
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<String> listStreams(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("prefix must not be null");
        }
        List<String> matches = new ArrayList<>();
        // streams map is only populated by append(), so every entry has
        // at least one record — the "empty placeholder" exclusion in the
        // port contract is honoured for free.
        for (String streamId : streams.keySet()) {
            if (streamId.startsWith(prefix)) {
                matches.add(streamId);
            }
        }
        return List.copyOf(matches);
    }

    /** {@inheritDoc} */
    @Override
    public List<EventRecord> readFrom(String streamId, long afterSeq, int limit) {
        if (afterSeq < 0L) {
            throw new IllegalArgumentException("afterSeq must be >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        // Fast path before lock acquisition: avoid creating a per-stream lock entry
        // for a speculative read on a stream that has never been written to.
        if (!streams.containsKey(streamId)) {
            return List.of();
        }
        ReentrantLock lock = locks.computeIfAbsent(streamId, k -> new ReentrantLock());
        lock.lock();
        try {
            List<EventRecord> stream = streams.get(streamId);
            if (stream == null) {
                return List.of();
            }
            // Math.min bounds afterSeq by stream.size() (an int), so the cast is safe.
            int from = (int) Math.min(afterSeq, stream.size());
            int to = Math.min(stream.size(), from + limit);
            return List.copyOf(stream.subList(from, to));
        } finally {
            lock.unlock();
        }
    }
}
