package com.teya.ledger.infrastructure.port;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The on-the-wire shape of a stored event. Adapters read and write
 * {@link EventRecord}s; the application layer (specifically
 * {@code EventEnvelopeMapper} in Task 3.0) bridges between these and
 * the typed domain event records (e.g., {@code AccountEvent}).
 *
 * <p>{@code seq} is assigned by the store on append. Use
 * {@link #unsequenced} when constructing a record for an append; the
 * store will overwrite the placeholder.
 *
 * @param seq        per-stream monotonic sequence number, assigned by the store.
 * @param eventId    a globally-unique id for this record.
 * @param type       discriminator (e.g., {@code "MoneyDeposited"}).
 * @param occurredAt event timestamp.
 * @param payload    event-specific fields, JSON-friendly types only;
 *                   defensively copied on construction.
 */
public record EventRecord(
    long seq,
    UUID eventId,
    String type,
    Instant occurredAt,
    Map<String, Object> payload
) {

    /** Compact constructor enforcing non-null fields and defensively copying the payload map. */
    public EventRecord {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
        payload = Map.copyOf(payload);
    }

    /**
     * Constructs an unsequenced record for an append operation; the
     * store assigns {@code seq} during {@link EventStore#append}.
     *
     * @param eventId    a globally-unique id for this record.
     * @param type       discriminator (e.g., {@code "MoneyDeposited"}).
     * @param occurredAt event timestamp.
     * @param payload    event-specific fields.
     * @return new {@link EventRecord} with {@code seq = 0}.
     */
    public static EventRecord unsequenced(UUID eventId, String type,
                                          Instant occurredAt, Map<String, Object> payload) {
        return new EventRecord(0L, eventId, type, occurredAt, payload);
    }
}
