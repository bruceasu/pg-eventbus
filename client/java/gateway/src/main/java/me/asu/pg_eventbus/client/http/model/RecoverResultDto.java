package me.asu.pg_eventbus.client.http.model;

public record RecoverResultDto(
        long initializedOffset,
        ConsumerDto consumer
) {
}