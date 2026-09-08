package com.lifehub.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uniform envelope for every REST response (06-API-SPEC.md 1).
 *
 * <p>A success carries {@code data} and omits {@code error}; a failure carries {@code error} and
 * omits {@code data}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
