package me.asu.pg_eventbus.client.http.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record EventDto(
        long id,
        String sourceSystem,
        String targetSystem,
        String eventType,
        JsonNode payload,
        Instant createdAt
) {
}