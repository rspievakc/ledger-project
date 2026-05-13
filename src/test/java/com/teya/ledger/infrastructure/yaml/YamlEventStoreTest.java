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
        assertThat(appended.lastSeq()).isEqualTo(1L);
        assertThat(appended.records()).hasSize(1);
        assertThat(appended.records().get(0).seq()).isEqualTo(1L);

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

    @Test
    void read_rejects_non_positive_limit() {
        assertThatThrownBy(() -> store.readFrom("s", 0L, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void read_rejects_negative_after() {
        assertThatThrownBy(() -> store.readFrom("s", -1L, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void read_from_after_seq_beyond_end_returns_empty() {
        store.append("s", List.of(rec("E1"), rec("E2"), rec("E3")));
        assertThat(store.readFrom("s", 10L, 100)).isEmpty();
    }

    @Test
    void list_streams_returns_only_streams_matching_prefix() {
        store.append("account-a", List.of(rec("E1")));
        store.append("account-b", List.of(rec("E1")));
        store.append("customers", List.of(rec("E1")));
        assertThat(store.listStreams("account-"))
            .containsExactlyInAnyOrder("account-a", "account-b");
    }

    @Test
    void list_streams_with_empty_prefix_returns_all_streams() {
        store.append("account-a", List.of(rec("E1")));
        store.append("customers", List.of(rec("E1")));
        assertThat(store.listStreams(""))
            .containsExactlyInAnyOrder("account-a", "customers");
    }

    @Test
    void list_streams_returns_empty_when_root_is_empty() {
        assertThat(store.listStreams("account-")).isEmpty();
    }

    @Test
    void list_streams_finds_streams_persisted_across_recreation() {
        store.append("account-a", List.of(rec("E1")));
        EventStore fresh = new YamlEventStore(tempDir);
        // No appends on `fresh` — it must still discover the on-disk
        // stream so a restart can answer "list streams" before any
        // explicit read or write reloads it.
        assertThat(fresh.listStreams("account-")).containsExactly("account-a");
    }

    private EventRecord rec(String type) {
        return new EventRecord(
            0L, UUID.randomUUID(), type,
            Instant.parse("2026-05-06T10:00:00Z"),
            Map.of("k", "v"));
    }
}
