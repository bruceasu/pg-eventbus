package me.asu.pg_eventbus.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bus")
public record PgEventBusProperties(
        boolean enabled,
        String consumerName,
        int batchSize,
        int lockTtlSeconds,
        int maxRetries,
        String schema
) {
    public PgEventBusProperties {
        schema = schema == null || schema.isBlank() ? "bus" : schema.trim();
    }

    public PgEventBusProperties(
            boolean enabled,
            String consumerName,
            int batchSize,
            int lockTtlSeconds,
            int maxRetries
    ) {
        this(enabled, consumerName, batchSize, lockTtlSeconds, maxRetries, "bus");
    }
}