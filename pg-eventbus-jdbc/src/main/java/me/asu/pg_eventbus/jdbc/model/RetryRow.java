package me.asu.pg_eventbus.jdbc.model;

import com.fasterxml.jackson.databind.JsonNode;

public record RetryRow(
        long retryId,
        long originalId,
        JsonNode payload,
        int retryCount
) {
}