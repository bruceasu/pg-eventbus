package me.asu.pg_eventbus.gateway.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConsumerCommitRequest(
        @NotBlank String instanceId,
        @NotNull Long newLastId
) {
}