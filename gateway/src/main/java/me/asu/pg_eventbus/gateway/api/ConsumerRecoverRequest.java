package me.asu.pg_eventbus.gateway.api;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ConsumerRecoverRequest(
        @NotNull Instant startTime
) {
}