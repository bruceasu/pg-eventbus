package me.asu.pg_eventbus.client.http.model;

public record FieldErrorDto(
        String field,
        String rejectedValue,
        String message
) {
}