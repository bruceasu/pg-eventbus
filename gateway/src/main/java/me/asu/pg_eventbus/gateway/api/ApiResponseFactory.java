package me.asu.pg_eventbus.gateway.api;

import me.asu.pg_eventbus.gateway.model.ApiResponse;
import me.asu.pg_eventbus.gateway.model.ErrorDto;

import java.time.Instant;

final class ApiResponseFactory {

    private ApiResponseFactory() {
    }

    static <T> ApiResponse<T> ok(T data, String requestId, String message) {
        return new ApiResponse<>(true, "OK", message, requestId, Instant.now(), data, null);
    }

    static <T> ApiResponse<T> failed(String code, String message, String requestId) {
        return new ApiResponse<>(false, code, message, requestId, Instant.now(), null, new ErrorDto(code, message, java.util.List.of(), java.util.List.of()));
    }
}