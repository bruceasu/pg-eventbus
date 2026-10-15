package me.asu.pg_eventbus.cli.model;

public record CommitResultDto(
        boolean committed,
        long newLastCommittedEventId
) {
}