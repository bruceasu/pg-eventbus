package me.asu.pg_eventbus.core;

public record PgEventBusRunResult(
        boolean lockAcquired,
        int processedCount,
        long committedOffset,
        boolean retryScheduled
) {
}