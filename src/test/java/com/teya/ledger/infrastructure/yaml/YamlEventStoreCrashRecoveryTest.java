package com.teya.ledger.infrastructure.yaml;

import com.teya.ledger.infrastructure.port.AppendResult;
import com.teya.ledger.infrastructure.port.EventRecord;
import com.teya.ledger.infrastructure.port.EventStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class YamlEventStoreCrashRecoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void torn_final_document_is_ignored_on_read() throws Exception {
        EventStore writer = new YamlEventStore(tempDir);
        writer.append("s", List.of(rec("E1"), rec("E2")));

        // Simulate a crash mid-write by appending an incomplete YAML document.
        Path file = tempDir.resolve("s.yaml");
        Files.write(
            file,
            "---\nseq: 3\neventId: ".getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.APPEND);

        EventStore fresh = new YamlEventStore(tempDir);
        List<EventRecord> all = fresh.readFrom("s", 0L, 100);
        assertThat(all).extracting(EventRecord::seq).containsExactly(1L, 2L);

        // A subsequent append continues from seq 3, and the file is now clean
        // (recoverFile rewrote it on initStream): subsequent reads return
        // exactly the three valid records, with no parser error from the
        // torn region.
        AppendResult next = fresh.append("s", List.of(rec("E3")));
        assertThat(next.firstSeq()).isEqualTo(3L);

        List<EventRecord> afterRecover = fresh.readFrom("s", 0L, 100);
        assertThat(afterRecover).extracting(EventRecord::seq).containsExactly(1L, 2L, 3L);
    }

    @Test
    void empty_file_is_treated_as_empty_stream() throws Exception {
        Files.createFile(tempDir.resolve("s.yaml"));
        EventStore store = new YamlEventStore(tempDir);
        assertThat(store.readFrom("s", 0L, 10)).isEmpty();
        AppendResult appended = store.append("s", List.of(rec("E1")));
        assertThat(appended.firstSeq()).isEqualTo(1L);
    }

    private EventRecord rec(String type) {
        return new EventRecord(
            0L, UUID.randomUUID(), type,
            Instant.parse("2026-05-06T10:00:00Z"),
            Map.of("k", "v"));
    }
}
