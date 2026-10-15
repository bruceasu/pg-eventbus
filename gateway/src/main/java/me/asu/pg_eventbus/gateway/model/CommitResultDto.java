package me.asu.pg_eventbus.gateway.model;

public record CommitResultDto(
        boolean committed,
        long newLastCommittedEventId
) {
}