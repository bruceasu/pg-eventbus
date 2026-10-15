package me.asu.pg_eventbus.client.http.model;

import java.time.Instant;
import java.util.List;

public record MaintenanceResultDto(
        boolean executed,
        List<String> actions,
        Instant completedAt
) {
}