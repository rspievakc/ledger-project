package com.teya.ledger.infrastructure.port;

import java.util.List;

/**
 * The persistence port for the event-sourced ledger.
 *
 * <p>Implementations must guarantee:
 * <ul>
 *   <li>Appends to the same {@code streamId} are serialised so
 *       sequence numbers are dense and monotonic.</li>
 *   <li>Appends are durable before the call returns (e.g., {@code fsync}
 *       on a file-backed adapter). Once an append returns, the events
 *       must survive a process crash.</li>
 *   <li>{@link #readFrom} returns events with {@code seq > afterSeq}, in
 *       ascending {@code seq} order, never including a torn or
 *       partially-written record.</li>
 *   <li>Streams are independent: a slow append on stream A must not
 *       block reads or appends on stream B.</li>
 * </ul>
 */
public interface EventStore {

    /**
     * Atomically appends {@code events} to {@code streamId}.
     *
     * <p>The store assigns dense, monotonic sequence numbers starting
     * at {@code currentLastSeq + 1}.
     *
     * @param streamId logical stream identifier (e.g., {@code "account-<uuid>"}).
     * @param events   one or more unsequenced {@link EventRecord}s.
     * @return the assigned sequences and persisted records.
     */
    AppendResult append(String streamId, List<EventRecord> events);

    /**
     * Reads up to {@code limit} records from {@code streamId} with
     * {@code seq > afterSeq}.
     *
     * @param streamId logical stream identifier.
     * @param afterSeq exclusive lower bound; pass {@code 0} to start from the beginning.
     * @param limit    maximum number of records to return; must be {@code > 0}.
     * @return ordered list, possibly empty if no more events exist.
     */
    List<EventRecord> readFrom(String streamId, long afterSeq, int limit);

    /**
     * Lists every stream id whose name starts with {@code prefix}.
     *
     * <p>Used by application services that need to enumerate aggregates
     * of a kind without a separate index — e.g. listing every account
     * stream ({@code "account-"}) when resolving "all accounts for
     * customer X". Returned ids are the same logical identifiers used
     * by {@link #append} and {@link #readFrom}; order is unspecified
     * and callers must not depend on it.
     *
     * <p>A stream id is considered to exist once at least one record
     * has been appended to it; empty placeholder streams are not
     * reported.
     *
     * @param prefix non-null prefix to match; pass an empty string to
     *               list every stream.
     * @return list of matching stream ids, possibly empty.
     */
    List<String> listStreams(String prefix);
}
