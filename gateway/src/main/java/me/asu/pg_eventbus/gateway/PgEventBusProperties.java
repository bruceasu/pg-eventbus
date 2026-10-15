package me.asu.pg_eventbus.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bus")
public class PgEventBusProperties {
    private String schema = "bus";
    private int batchSize = 100;
    private int lockTtlSeconds = 30;

    public String schema() { return schema; }
    public int batchSize() { return batchSize; }
    public int lockTtlSeconds() { return lockTtlSeconds; }

    public void setSchema(String schema) {
        this.schema = schema == null || schema.isBlank() ? "bus" : schema.trim();
    }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public void setLockTtlSeconds(int lockTtlSeconds) { this.lockTtlSeconds = lockTtlSeconds; }
}
