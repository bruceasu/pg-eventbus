package me.asu.pg_eventbus.gateway.model;

public record FieldErrorDto(
        String field,
        String rejectedValue,
        String message
) {
}