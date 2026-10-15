package me.asu.pg_eventbus.client.http.model;

public record CommitResultDto(
        boolean committed,
        long newLastCommittedEventId
) {
}