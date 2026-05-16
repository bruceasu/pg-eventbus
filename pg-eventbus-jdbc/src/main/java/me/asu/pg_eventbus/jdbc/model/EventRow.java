package me.asu.pg_eventbus.jdbc.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record EventRow(
        long id,
        String sourceSystem,
        String targetSystem,
        String eventType,
        JsonNode payload,
        Instant createdAt
) {
}