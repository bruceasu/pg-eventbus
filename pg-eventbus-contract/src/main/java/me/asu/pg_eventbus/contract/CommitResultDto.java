package me.asu.pg_eventbus.contract;

public record CommitResultDto(
        boolean committed,
        long newLastCommittedEventId
) {
}