package com.teya.ledger.infrastructure.yaml;

import com.teya.ledger.infrastructure.port.EventRecord;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serialises {@link EventRecord}s to YAML documents and back. Each
 * record is written as a single mapping prefixed by the YAML document
 * separator {@code ---}, allowing a stream file to be parsed
 * incrementally and a torn final document to be detected and
 * truncated on recovery.
 */
final class YamlEventCodec {

    private final Yaml yaml;

    YamlEventCodec() {
        DumperOptions dump = new DumperOptions();
        dump.setExplicitStart(true);
        dump.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Representer representer = new Representer(dump);
        LoaderOptions load = new LoaderOptions();
        load.setAllowDuplicateKeys(false);
        this.yaml = new Yaml(new SafeConstructor(load), representer, dump);
    }

    /**
     * Serialises a sequenced record to a single YAML document.
     */
    String encode(EventRecord record) {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("seq", record.seq());
        mapping.put("eventId", record.eventId().toString());
        mapping.put("type", record.type());
        mapping.put("occurredAt", record.occurredAt().toString());
        mapping.put("payload", new LinkedHashMap<>(record.payload()));
        return yaml.dump(mapping);
    }

    /**
     * Decodes a single mapping (one YAML document) into an
     * {@link EventRecord}. Returns {@code null} if the mapping is
     * structurally incomplete (a torn write at the file tail).
     */
    EventRecord decode(Map<String, Object> mapping) {
        if (mapping == null) {
            return null;
        }
        Object seq = mapping.get("seq");
        Object eventId = mapping.get("eventId");
        Object type = mapping.get("type");
        Object occurredAt = mapping.get("occurredAt");
        Object payload = mapping.get("payload");
        if (seq == null || eventId == null || type == null
            || occurredAt == null || payload == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> rawPayload = (Map<String, Object>) payload;
        // SnakeYAML deserialises integers that fit in 32 bits as Integer, even when
        // they were originally stored as Long. Normalise all Integer values to Long
        // so the round-trip contract (payload contains Long for numeric amounts) holds.
        Map<String, Object> payloadMap = rawPayload.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue() instanceof Integer i ? i.longValue() : e.getValue(),
                (a, b) -> b,
                LinkedHashMap::new
            ));
        return new EventRecord(
            ((Number) seq).longValue(),
            UUID.fromString(eventId.toString()),
            type.toString(),
            Instant.parse(occurredAt.toString()),
            payloadMap
        );
    }

    Iterable<Object> loadAll(String text) {
        return yaml.loadAll(text);
    }
}
