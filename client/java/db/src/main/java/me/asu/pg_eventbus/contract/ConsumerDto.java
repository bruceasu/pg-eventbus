package me.asu.pg_eventbus.contract;

import java.time.Instant;

public record ConsumerDto(
        String consumerName,
        String instanceId,
        boolean locked,
        String lockOwner,
        Instant lockAcquiredAt,
        Instant lockExpiresAt,
        long lastCommittedEventId,
        long currentOffset,
        int batchSize
) {
}