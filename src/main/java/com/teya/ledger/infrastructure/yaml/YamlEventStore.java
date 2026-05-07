package com.teya.ledger.infrastructure.yaml;

import com.teya.ledger.infrastructure.port.AppendResult;
import com.teya.ledger.infrastructure.port.EventRecord;
import com.teya.ledger.infrastructure.port.EventStore;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * File-backed {@link EventStore} that writes one append-only YAML
 * document per event under a per-stream file. Each append is
 * fsync'd before returning, so any record reported as persisted
 * survives a process crash.
 *
 * <p><strong>Crash recovery.</strong> If a process is killed mid-append
 * the stream file may end with a partially-written YAML document. On
 * the first access to that stream after restart, {@code initStream}
 * detects the malformed tail (either a structurally-incomplete
 * mapping or a {@link YAMLException} from the parser), recovers by
 * rewriting the file with only the valid records (atomic temp +
 * rename), and resumes appending from {@code lastValidSeq + 1}. No
 * record reported as successful to a caller can be lost — the
 * fsync precedes the response — and no torn bytes are left to corrupt
 * future reads.
 *
 * <p><strong>Concurrency.</strong> A single-process service is the
 * design assumption; an in-process per-stream {@link ReentrantLock}
 * guards every read and write. A future multi-process deployment
 * would need to add an OS-level advisory {@code FileLock} on top.
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
            long lastSeq = lastSeqs.computeIfAbsent(streamId, this::initStream);
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
            writeFsyncedAppend(streamId, buf.toString());
            long newLast = firstSeq + assigned.size() - 1L;
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
        // Fast path: if the file does not exist and never has, no recovery
        // or lock acquisition is needed. A concurrent append from another
        // thread could create the file between this check and the lock
        // below, but in that case we already serialise via the per-stream
        // lock and re-read inside. The narrow visibility race is bounded
        // by `volatile` semantics of ConcurrentHashMap and is acceptable
        // for an at-most-stale-by-one-append read of a stream that has
        // never been touched.
        if (!Files.exists(file) && !lastSeqs.containsKey(streamId)) {
            return List.of();
        }
        ReentrantLock lock = streamLocks.computeIfAbsent(streamId, k -> new ReentrantLock());
        lock.lock();
        try {
            // Trigger first-access recovery before reading, so torn writes
            // from a previous process crash are repaired before the caller
            // sees them.
            lastSeqs.computeIfAbsent(streamId, this::initStream);
            if (!Files.exists(file)) {
                return List.of();
            }
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
     * First-access initialisation for a stream. Reads the file, validates
     * the document sequence, recovers any torn tail by rewriting the file
     * atomically with only the valid records, and returns the resulting
     * last seq.
     *
     * <p>Returns {@code 0} when the file does not exist.
     */
    private long initStream(String streamId) {
        Path file = layout.fileFor(streamId);
        if (!Files.exists(file)) {
            return 0L;
        }
        // Parse all valid records, tolerating a torn tail.
        List<EventRecord> valid = readAllRecords(file);

        // If the file has trailing bytes beyond what we could parse, rewrite
        // it to contain exactly the valid records — preserves the strict
        // append-only invariant for subsequent appends and reads.
        long fileSize;
        try {
            fileSize = Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to stat " + file, e);
        }
        long expectedSize = 0L;
        for (EventRecord r : valid) {
            expectedSize += codec.encode(r).getBytes(StandardCharsets.UTF_8).length;
        }
        if (fileSize != expectedSize) {
            recoverFile(file, valid);
        }

        long last = 0L;
        for (EventRecord r : valid) {
            if (r.seq() > last) {
                last = r.seq();
            }
        }
        return last;
    }

    /**
     * Reads and decodes valid YAML documents from a stream file. A torn
     * document at the tail surfaces either as a {@link YAMLException}
     * from the lazy parser or as a structurally-incomplete mapping (the
     * codec's {@code decode} returns {@code null}); both are treated as
     * end-of-stream and the partial document is silently skipped.
     */
    private List<EventRecord> readAllRecords(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + file, e);
        }
        List<EventRecord> records = new ArrayList<>();
        Iterator<Object> it = codec.loadAll(text).iterator();
        while (true) {
            Object doc;
            try {
                if (!it.hasNext()) {
                    break;
                }
                doc = it.next();
            } catch (YAMLException e) {
                // Torn write at the file tail — parser cannot complete
                // the document. Stop reading; the caller never saw this
                // record succeed.
                break;
            }
            if (!(doc instanceof Map<?, ?> mapping)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            EventRecord rec = codec.decode((Map<String, Object>) mapping);
            if (rec == null) {
                // Structurally-incomplete mapping = also a torn tail.
                break;
            }
            records.add(rec);
        }
        return records;
    }

    /**
     * Rewrites the stream file to contain exactly {@code valid}, using
     * atomic temp + rename. Called only from {@link #initStream} when
     * a torn tail has been detected.
     */
    private void recoverFile(Path file, List<EventRecord> valid) {
        StringBuilder buf = new StringBuilder();
        for (EventRecord r : valid) {
            buf.append(codec.encode(r));
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".recover");
        try (FileChannel ch = FileChannel.open(
            tmp,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )) {
            writeAll(ch, buf.toString().getBytes(StandardCharsets.UTF_8));
            ch.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write recovery file " + tmp, e);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to atomically replace " + file, e);
        }
    }

    /**
     * Appends {@code chunk} to the stream file via a FileChannel in
     * APPEND mode, then calls {@code force(true)} (fsync data + metadata)
     * before returning. The write is performed in a loop because
     * {@code FileChannel.write(ByteBuffer)} may return fewer bytes than
     * requested per its specification, and a partial write would leave
     * a record on disk that is structurally valid YAML but semantically
     * truncated — a class of corruption the torn-tail recovery cannot
     * detect.
     */
    private void writeFsyncedAppend(String streamId, String chunk) {
        Path file = layout.fileFor(streamId);
        byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
        try (FileChannel ch = FileChannel.open(
            file,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )) {
            writeAll(ch, bytes);
            ch.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append to " + file, e);
        }
    }

    /** Writes the full byte array, retrying around any short writes. */
    private static void writeAll(FileChannel ch, byte[] bytes) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        while (buf.hasRemaining()) {
            int written = ch.write(buf);
            if (written < 0) {
                throw new IOException("FileChannel.write returned " + written);
            }
        }
    }
}
