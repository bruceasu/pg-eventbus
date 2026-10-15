package me.asu.pg_eventbus.gateway.model;

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