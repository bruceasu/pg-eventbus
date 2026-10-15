package me.asu.pg_eventbus.gateway.model;

import java.util.List;

public record ErrorDto(
        String errorCode,
        String errorMessage,
        List<String> details,
        List<FieldErrorDto> fieldErrors
) {
}