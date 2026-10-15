package me.asu.pg_eventbus.client.http.model;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        String requestId,
        Instant timestamp,
        T data,
        ErrorDto error
) {
}