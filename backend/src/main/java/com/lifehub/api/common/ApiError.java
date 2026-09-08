package com.lifehub.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Error body shape from 04-ARCHITECTURE.md 8.
 *
 * <p>{@code message} is user facing and always in Vietnamese. It must explain the cause and suggest
 * a way forward, and must never contain a stack trace (NFR-USE-03).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String field, String traceId) {

    public static ApiError of(ErrorCode code, String message, String traceId) {
        return new ApiError(code.name(), message, null, traceId);
    }

    public static ApiError of(ErrorCode code, String message, String field, String traceId) {
        return new ApiError(code.name(), message, field, traceId);
    }
}
