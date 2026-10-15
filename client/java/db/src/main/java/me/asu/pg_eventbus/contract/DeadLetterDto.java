package me.asu.pg_eventbus.contract;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeadLetterDto(
        long deadLetterId,
        long originalEventId,
        String sourceSystem,
        String targetSystem,
        String eventType,
        JsonNode payload,
        String failureReason,
        int retryCount,
        Instant failedAt,
        Instant lastAttemptAt
) {
}