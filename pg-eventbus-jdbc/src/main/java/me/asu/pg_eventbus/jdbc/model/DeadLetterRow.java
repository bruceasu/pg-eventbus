package me.asu.pg_eventbus.jdbc.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeadLetterRow(
        long deadLetterId,
        Long originalEventId,
        JsonNode payload,
        String errorMessage,
        Instant failedAt
) {
}