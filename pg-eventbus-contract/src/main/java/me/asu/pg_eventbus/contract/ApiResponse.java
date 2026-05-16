package me.asu.pg_eventbus.contract;

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