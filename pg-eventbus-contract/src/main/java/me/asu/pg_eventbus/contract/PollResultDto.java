package me.asu.pg_eventbus.contract;

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