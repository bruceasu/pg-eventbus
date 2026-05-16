package me.asu.pg_eventbus.contract;

public record RetryResultDto(
        int processedCount,
        int movedToDeadLetterCount,
        int requeuedCount
) {
}