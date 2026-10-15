package me.asu.pg_eventbus.client.http.model;

import java.util.List;

public record ErrorDto(
        String errorCode,
        String errorMessage,
        List<String> details,
        List<FieldErrorDto> fieldErrors
) {
}