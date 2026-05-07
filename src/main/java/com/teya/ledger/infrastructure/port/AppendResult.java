package com.teya.ledger.infrastructure.port;

import java.util.List;

/**
 * Outcome of a successful {@link EventStore#append} call.
 *
 * @param firstSeq the {@code seq} assigned to the first event in the batch.
 * @param lastSeq  the {@code seq} assigned to the last event in the batch.
 * @param records  the records as persisted (with assigned sequences); defensively copied.
 */
public record AppendResult(long firstSeq, long lastSeq, List<EventRecord> records) {

    /** Compact constructor enforcing the seq invariant and copying the records list. */
    public AppendResult {
        if (lastSeq < firstSeq) {
            throw new IllegalArgumentException(
                "lastSeq (" + lastSeq + ") must be >= firstSeq (" + firstSeq + ")");
        }
        records = List.copyOf(records);
    }
}
