package me.asu.pg_eventbus.gateway.model;

public record RecoverResultDto(
        long initializedOffset,
        ConsumerDto consumer
) {
}