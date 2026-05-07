package com.teya.ledger.infrastructure.yaml;

import com.teya.ledger.infrastructure.port.AppendResult;
import com.teya.ledger.infrastructure.port.EventRecord;
import com.teya.ledger.infrastructure.port.EventStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * File-backed {@link EventStore} that writes one append-only YAML
 * document per event under a per-stream file. Each append is
 * fsync'd before returning, so any record reported as persisted
 * survives a process crash. A torn final document (write killed
 * before fsync completed) is detected on read and ignored, since the
 * caller never saw it succeed.
 *
 * <p>Concurrency model: per-stream {@link ReentrantLock}s — different
 * streams run in parallel; the same stream serialises.
 */
public final class YamlEventStore implements EventStore {

    private final StreamFileLayout layout;
    private final YamlEventCodec codec = new YamlEventCodec();
    private final Map<String, ReentrantLock> streamLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeqs = new ConcurrentHashMap<>();

    public YamlEventStore(Path root) {
        this.layout = new StreamFileLayout(root);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create stream root: " + root, e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public AppendResult append(String streamId, List<EventRecord> events) {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("events must not be empty");
        }
        ReentrantLock lock = streamLocks.computeIfAbsent(streamId, k -> new ReentrantLock());
        lock.lock();
        try {
            long lastSeq = lastSeqs.computeIfAbsent(streamId, this::loadLastSeq);
            long firstSeq = lastSeq + 1L;
            List<EventRecord> assigned = new ArrayList<>(events.size());
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < events.size(); i++) {
                EventRecord src = events.get(i);
                EventRecord seq = new EventRecord(
                    firstSeq + i, src.eventId(), src.type(),
                    src.occurredAt(), src.payload());
                assigned.add(seq);
                buf.append(codec.encode(seq));
            }
            writeAtomicAppend(streamId, buf.toString());
            long newLast = firstSeq + events.size() - 1L;
            lastSeqs.put(streamId, newLast);
            return new AppendResult(firstSeq, newLast, assigned);
        } finally {
            lock.unlock();
        }
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
        Path file = layout.fileFor(streamId);
        if (!Files.exists(file)) {
            return List.of();
        }
        ReentrantLock lock = streamLocks.computeIfAbsent(streamId, k -> new ReentrantLock());
        lock.lock();
        try {
            List<EventRecord> all = readAllRecords(file);
            List<EventRecord> page = new ArrayList<>();
            for (EventRecord r : all) {
                if (r.seq() <= afterSeq) {
                    continue;
                }
                page.add(r);
                if (page.size() >= limit) {
                    break;
                }
            }
            return List.copyOf(page);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Loads the last known sequence number from disk for a stream,
     * returning 0 if the file does not yet exist.
     */
    private long loadLastSeq(String streamId) {
        Path file = layout.fileFor(streamId);
        if (!Files.exists(file)) {
            return 0L;
        }
        long last = 0L;
        for (EventRecord r : readAllRecords(file)) {
            if (r.seq() > last) {
                last = r.seq();
            }
        }
        return last;
    }

    /**
     * Reads and decodes all intact YAML documents from a stream file.
     * Documents that fail structural validation (torn writes) are silently
     * skipped — the caller that wrote them never received a success response.
     */
    private List<EventRecord> readAllRecords(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + file, e);
        }
        List<EventRecord> records = new ArrayList<>();
        for (Object doc : codec.loadAll(text)) {
            if (!(doc instanceof Map<?, ?> mapping)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            EventRecord rec = codec.decode((Map<String, Object>) mapping);
            if (rec != null) {
                records.add(rec);
            }
            // null = torn write at tail; ignore. See class javadoc.
        }
        return records;
    }

    /**
     * Appends {@code chunk} to the stream file using a FileChannel so
     * we can call {@code force(true)} (fsync metadata + data) before
     * returning. This guarantees that any caller who receives a success
     * response will find their events on disk after a crash.
     */
    private void writeAtomicAppend(String streamId, String chunk) {
        Path file = layout.fileFor(streamId);
        byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
        try (var ch = java.nio.channels.FileChannel.open(
            file,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )) {
            ch.write(java.nio.ByteBuffer.wrap(bytes));
            ch.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append to " + file, e);
        }
    }
}
