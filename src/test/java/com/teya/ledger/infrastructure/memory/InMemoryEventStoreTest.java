package com.teya.ledger.infrastructure.memory;

import com.teya.ledger.infrastructure.port.AppendResult;
import com.teya.ledger.infrastructure.port.EventRecord;
import com.teya.ledger.infrastructure.port.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryEventStoreTest {

    private EventStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryEventStore();
    }

    @Test
    void append_assigns_sequential_seqs_starting_at_one() {
        AppendResult result = store.append("s1", List.of(
            unseq("E1"), unseq("E2"), unseq("E3")
        ));
        assertThat(result.firstSeq()).isEqualTo(1L);
        assertThat(result.lastSeq()).isEqualTo(3L);
        assertThat(result.records())
            .extracting(EventRecord::seq)
            .containsExactly(1L, 2L, 3L);
    }

    @Test
    void second_append_continues_seq() {
        store.append("s1", List.of(unseq("E1")));
        AppendResult second = store.append("s1", List.of(unseq("E2")));
        assertThat(second.firstSeq()).isEqualTo(2L);
        assertThat(second.lastSeq()).isEqualTo(2L);
    }

    @Test
    void streams_are_independent() {
        store.append("s1", List.of(unseq("E1"), unseq("E2")));
        AppendResult result = store.append("s2", List.of(unseq("E1")));
        assertThat(result.firstSeq()).isEqualTo(1L);
    }

    @Test
    void read_from_empty_stream_returns_empty() {
        assertThat(store.readFrom("missing", 0L, 10)).isEmpty();
    }

    @Test
    void read_from_returns_after_cursor() {
        store.append("s1", List.of(unseq("E1"), unseq("E2"), unseq("E3")));
        List<EventRecord> tail = store.readFrom("s1", 1L, 10);
        assertThat(tail).extracting(EventRecord::seq).containsExactly(2L, 3L);
    }

    @Test
    void read_from_honours_limit() {
        store.append("s1", List.of(unseq("E1"), unseq("E2"), unseq("E3")));
        List<EventRecord> page = store.readFrom("s1", 0L, 2);
        assertThat(page).hasSize(2);
        assertThat(page).extracting(EventRecord::seq).containsExactly(1L, 2L);
    }

    @Test
    void append_rejects_empty_list() {
        assertThatThrownBy(() -> store.append("s1", List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void read_rejects_non_positive_limit() {
        assertThatThrownBy(() -> store.readFrom("s1", 0L, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void read_rejects_negative_after() {
        assertThatThrownBy(() -> store.readFrom("s1", -1L, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void read_from_after_seq_beyond_end_returns_empty() {
        store.append("s1", List.of(unseq("E1"), unseq("E2"), unseq("E3")));
        assertThat(store.readFrom("s1", 10L, 100)).isEmpty();
    }

    @Test
    void list_streams_returns_only_streams_matching_prefix() {
        store.append("account-a", List.of(unseq("E1")));
        store.append("account-b", List.of(unseq("E1")));
        store.append("customers", List.of(unseq("E1")));
        assertThat(store.listStreams("account-"))
            .containsExactlyInAnyOrder("account-a", "account-b");
    }

    @Test
    void list_streams_with_empty_prefix_returns_all_streams() {
        store.append("account-a", List.of(unseq("E1")));
        store.append("customers", List.of(unseq("E1")));
        assertThat(store.listStreams(""))
            .containsExactlyInAnyOrder("account-a", "customers");
    }

    @Test
    void list_streams_returns_empty_when_no_streams_exist() {
        assertThat(store.listStreams("account-")).isEmpty();
    }

    @Test
    void list_streams_skips_streams_with_no_appended_events() {
        store.append("account-a", List.of(unseq("E1")));
        // Speculative read does not create the stream — the fast-path
        // in readFrom avoids materialising a per-stream lock entry, so
        // "account-ghost" must not show up in listStreams afterwards.
        store.readFrom("account-ghost", 0L, 10);
        assertThat(store.listStreams("account-")).containsExactly("account-a");
    }

    private EventRecord unseq(String type) {
        return EventRecord.unsequenced(
            UUID.randomUUID(), type, Instant.now(), Map.of("dummy", true));
    }
}
