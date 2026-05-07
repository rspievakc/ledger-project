package com.teya.ledger.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Type-safe binding for the {@code ledger.*} configuration namespace
 * in {@code application.yaml}.
 */
@ConfigurationProperties("ledger")
public record LedgerProperties(
    Storage storage,
    Idempotency idempotency
) {

    public record Storage(String type, Yaml yaml) {
        public record Yaml(String directory) {}
    }

    public record Idempotency(int cacheSize, Duration ttl) {}
}
