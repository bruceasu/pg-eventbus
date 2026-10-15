package me.asu.pg_eventbus.gateway.api;

import jakarta.validation.constraints.NotBlank;

public record ConsumerPollRequest(
        @NotBlank String instanceId,
        Integer limit
) {
}