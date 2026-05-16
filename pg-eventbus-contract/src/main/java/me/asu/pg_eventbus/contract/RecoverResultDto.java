package me.asu.pg_eventbus.contract;

public record RecoverResultDto(
        long initializedOffset,
        ConsumerDto consumer
) {
}