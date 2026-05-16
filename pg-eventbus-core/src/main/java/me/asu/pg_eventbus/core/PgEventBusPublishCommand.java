package me.asu.pg_eventbus.core;

import com.fasterxml.jackson.databind.JsonNode;

public record PgEventBusPublishCommand(
        String sourceSystem,
        String targetSystem,
        String eventType,
        JsonNode payload
) {
}