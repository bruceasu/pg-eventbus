package me.asu.pg_eventbus.client.http.model;

public record RetryResultDto(
        int processedCount,
        int movedToDeadLetterCount,
        int requeuedCount
) {
}