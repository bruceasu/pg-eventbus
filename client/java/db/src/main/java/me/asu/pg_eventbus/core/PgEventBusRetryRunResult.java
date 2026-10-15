package me.asu.pg_eventbus.core;

public record PgEventBusRetryRunResult(
        boolean lockAcquired,
        int processedCount,
        int retriedCount,
        int deadletteredCount
) {
}