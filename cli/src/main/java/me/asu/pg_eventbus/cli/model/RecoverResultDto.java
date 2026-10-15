package me.asu.pg_eventbus.cli.model;

public record RecoverResultDto(
        long initializedOffset,
        ConsumerDto consumer
) {
}