package me.asu.pg_eventbus.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bus")
public class PgEventBusProperties {

    private boolean enabled;
    private String consumerName;
    private int batchSize;
    private int lockTtlSeconds;
    private int maxRetries;
    private String schema = "bus";

    public PgEventBusProperties() {
    }

    public PgEventBusProperties(
            boolean enabled,
            String consumerName,
            int batchSize,
            int lockTtlSeconds,
            int maxRetries,
            String schema
    ) {
        this.enabled = enabled;
        this.consumerName = consumerName;
        this.batchSize = batchSize;
        this.lockTtlSeconds = lockTtlSeconds;
        this.maxRetries = maxRetries;
        setSchema(schema);
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

    public boolean enabled() {
        return enabled;
    }

    public String consumerName() {
        return consumerName;
    }

    public int batchSize() {
        return batchSize;
    }

    public int lockTtlSeconds() {
        return lockTtlSeconds;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public String schema() {
        return schema;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setLockTtlSeconds(int lockTtlSeconds) {
        this.lockTtlSeconds = lockTtlSeconds;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public void setSchema(String schema) {
        this.schema = schema == null || schema.isBlank() ? "bus" : schema.trim();
    }
}
