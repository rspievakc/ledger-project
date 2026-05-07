package com.teya.ledger.infrastructure.yaml;

import com.teya.ledger.infrastructure.port.AppendResult;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlEventStoreTest {

    @TempDir
    Path tempDir;

    private EventStore store;

    @BeforeEach
    void setUp() {
        store = new YamlEventStore(tempDir);
    }

    @Test
    void append_then_read_roundtrips_all_fields() {
        UUID eventId = UUID.randomUUID();
        Instant ts = Instant.parse("2026-05-06T10:14:23.118Z");
        Map<String, Object> payload = Map.of(
            "accountId", "9b1f-uuid",
            "amountMinorUnits", 5000L,
            "currency", "GBP",
            "idempotencyKey", "dep-abc-123"
        );
        AppendResult appended = store.append("account-9b1f", List.of(
            new EventRecord(0L, eventId, "MoneyDeposited", ts, payload)
        ));
        assertThat(appended.firstSeq()).isEqualTo(1L);

        List<EventRecord> read = store.readFrom("account-9b1f", 0L, 10);
        assertThat(read).hasSize(1);
        EventRecord r = read.get(0);
        assertThat(r.seq()).isEqualTo(1L);
        assertThat(r.eventId()).isEqualTo(eventId);
        assertThat(r.type()).isEqualTo("MoneyDeposited");
        assertThat(r.occurredAt()).isEqualTo(ts);
        assertThat(r.payload())
            .containsEntry("accountId", "9b1f-uuid")
            .containsEntry("amountMinorUnits", 5000L)
            .containsEntry("currency", "GBP")
            .containsEntry("idempotencyKey", "dep-abc-123");
    }

    @Test
    void second_append_continues_seq() {
        store.append("s", List.of(rec("E1")));
        AppendResult second = store.append("s", List.of(rec("E2")));
        assertThat(second.firstSeq()).isEqualTo(2L);
    }

    @Test
    void read_from_returns_empty_for_unknown_stream() {
        assertThat(store.readFrom("missing", 0L, 10)).isEmpty();
    }

    @Test
    void read_from_honours_after_and_limit() {
        store.append("s", List.of(rec("E1"), rec("E2"), rec("E3"), rec("E4")));
        List<EventRecord> page = store.readFrom("s", 1L, 2);
        assertThat(page).extracting(EventRecord::seq).containsExactly(2L, 3L);
    }

    @Test
    void append_persists_across_store_recreation() {
        store.append("s", List.of(rec("E1"), rec("E2")));
        EventStore fresh = new YamlEventStore(tempDir);
        AppendResult third = fresh.append("s", List.of(rec("E3")));
        assertThat(third.firstSeq()).isEqualTo(3L);
        List<EventRecord> all = fresh.readFrom("s", 0L, 100);
        assertThat(all).extracting(EventRecord::type).containsExactly("E1", "E2", "E3");
    }

    @Test
    void append_rejects_empty_list() {
        assertThatThrownBy(() -> store.append("s", List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writes_one_file_per_stream() {
        store.append("account-a", List.of(rec("E1")));
        store.append("account-b", List.of(rec("E1")));
        assertThat(tempDir.resolve("account-a.yaml")).exists();
        assertThat(tempDir.resolve("account-b.yaml")).exists();
    }

    private EventRecord rec(String type) {
        return new EventRecord(
            0L, UUID.randomUUID(), type,
            Instant.parse("2026-05-06T10:00:00Z"),
            Map.of("k", "v"));
    }
}
