package com.teya.ledger.infrastructure.memory;

import com.teya.ledger.infrastructure.port.AppendResult;
import com.teya.ledger.infrastructure.port.EventRecord;
import com.teya.ledger.infrastructure.port.EventStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Process-local {@link EventStore} backed by in-memory lists.
 *
 * <p>Used in unit tests and (optionally) at runtime via
 * {@code ledger.storage.type=in-memory}. Holds events for the lifetime
 * of the process; restarts lose all state.
 */
public final class InMemoryEventStore implements EventStore {

    private final Map<String, List<EventRecord>> streams = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public AppendResult append(String streamId, List<EventRecord> events) {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("events must not be empty");
        }
        ReentrantLock lock = locks.computeIfAbsent(streamId, k -> new ReentrantLock());
        lock.lock();
        try {
            List<EventRecord> stream = streams.computeIfAbsent(
                streamId, k -> Collections.synchronizedList(new ArrayList<>()));
            long firstSeq = stream.size() + 1L;
            List<EventRecord> assigned = new ArrayList<>(events.size());
            for (int i = 0; i < events.size(); i++) {
                EventRecord src = events.get(i);
                assigned.add(new EventRecord(
                    firstSeq + i, src.eventId(), src.type(),
                    src.occurredAt(), src.payload()));
            }
            stream.addAll(assigned);
            return new AppendResult(firstSeq, firstSeq + events.size() - 1L, assigned);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<EventRecord> readFrom(String streamId, long afterSeq, int limit) {
        if (afterSeq < 0L) {
            throw new IllegalArgumentException("afterSeq must be >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        List<EventRecord> stream = streams.get(streamId);
        if (stream == null) {
            return List.of();
        }
        synchronized (stream) {
            int from = (int) Math.min(afterSeq, stream.size());
            int to = Math.min(stream.size(), from + limit);
            return List.copyOf(stream.subList(from, to));
        }
    }
}
