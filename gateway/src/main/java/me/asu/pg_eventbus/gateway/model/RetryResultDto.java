package me.asu.pg_eventbus.gateway.model;

public record RetryResultDto(
        int processedCount,
        int movedToDeadLetterCount,
        int requeuedCount
) {
}