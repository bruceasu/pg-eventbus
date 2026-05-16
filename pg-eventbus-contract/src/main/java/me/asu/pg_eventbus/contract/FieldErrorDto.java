package me.asu.pg_eventbus.contract;

public record FieldErrorDto(
        String field,
        String rejectedValue,
        String message
) {
}