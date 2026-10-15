package me.asu.pg_eventbus.cli.model;

import java.time.Instant;
import java.util.List;

public record PollResultDto(
        ConsumerDto consumer,
        List<EventDto> events,
        int batchSize,
        boolean lockAcquired,
        Instant polledAt
) {
}