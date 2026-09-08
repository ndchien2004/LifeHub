package com.lifehub.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lifehub.domain.common.ConflictException;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.common.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** T0-06 — exception type to error code and HTTP status mapping. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("T0-06 — ValidationException → 400 VALIDATION_ERROR, giữ tên trường")
    void mapsValidationException() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleValidation(new ValidationException("Số tiền phải lớn hơn 0", "amount"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiError error = requireError(response);
        assertThat(error.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.message()).isEqualTo("Số tiền phải lớn hơn 0");
        assertThat(error.field()).isEqualTo("amount");
        assertThat(error.traceId()).isNotBlank();
    }

    @Test
    @DisplayName("T0-06 — NotFoundException → 404 NOT_FOUND")
    void mapsNotFoundException() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotFound(new NotFoundException("Ví không tồn tại"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(requireError(response).code()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("T0-06 — ConflictException → 409 CONFLICT")
    void mapsConflictException() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleConflict(new ConflictException("Tên tag đã tồn tại", "name"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(requireError(response).code()).isEqualTo("CONFLICT");
    }

    @Test
    @DisplayName("T0-06 — lỗi không lường trước → 500 INTERNAL_ERROR, không lộ chi tiết kỹ thuật")
    void mapsUnexpectedExceptionWithoutLeakingDetails() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/bootstrap");

        ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpected(
                new IllegalStateException("connection pool exhausted at com.zaxxer.hikari"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiError error = requireError(response);
        assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.message())
                .as("NFR-USE-03: thông báo cho user, không phải thông điệp kỹ thuật")
                .doesNotContain("hikari")
                .doesNotContain("connection pool");
        assertThat(error.traceId()).isNotBlank();
    }

    @Test
    @DisplayName("Mỗi lỗi có traceId riêng để tra cứu trong log")
    void generatesADistinctTraceIdPerError() {
        String first = requireError(handler.handleNotFound(new NotFoundException("x"))).traceId();
        String second = requireError(handler.handleNotFound(new NotFoundException("x"))).traceId();

        assertThat(first).isNotEqualTo(second);
    }

    private ApiError requireError(ResponseEntity<ApiResponse<Void>> response) {
        ApiResponse<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.data()).isNull();
        assertThat(body.error()).isNotNull();
        return body.error();
    }
}
