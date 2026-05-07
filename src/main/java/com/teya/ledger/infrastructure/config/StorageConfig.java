package com.teya.ledger.infrastructure.config;

import com.teya.ledger.infrastructure.idempotency.InMemoryIdempotencyStore;
import com.teya.ledger.infrastructure.memory.InMemoryEventStore;
import com.teya.ledger.infrastructure.port.EventStore;
import com.teya.ledger.infrastructure.port.IdempotencyStore;
import com.teya.ledger.infrastructure.yaml.YamlEventStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Wires the {@link EventStore} adapter selected by
 * {@code ledger.storage.type} and the {@link IdempotencyStore}.
 */
@Configuration
@EnableConfigurationProperties(LedgerProperties.class)
public class StorageConfig {

    @Bean
    public EventStore eventStore(LedgerProperties props) {
        return switch (props.storage().type()) {
            case "yaml" -> new YamlEventStore(Path.of(props.storage().yaml().directory()));
            case "in-memory" -> new InMemoryEventStore();
            default -> throw new IllegalStateException(
                "unknown ledger.storage.type: " + props.storage().type());
        };
    }

    @Bean
    public IdempotencyStore idempotencyStore(LedgerProperties props, Clock clock) {
        return new InMemoryIdempotencyStore(
            props.idempotency().cacheSize(),
            props.idempotency().ttl(),
            clock
        );
    }
}
