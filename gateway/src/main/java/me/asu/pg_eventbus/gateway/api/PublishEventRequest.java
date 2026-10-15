package me.asu.pg_eventbus.gateway.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PublishEventRequest(
        @NotBlank String sourceSystem,
        @NotBlank String targetSystem,
        @NotBlank String eventType,
        @NotNull JsonNode payload
) {
}