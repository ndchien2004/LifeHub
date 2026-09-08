package com.lifehub.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** T0-06 (part 1) — every error code maps to the HTTP status fixed in 04-ARCHITECTURE.md §8. */
class ErrorCodeTest {

    @Test
    @DisplayName("T0-06 — bảng mã lỗi ánh xạ đúng HTTP status")
    void mapsEachCodeToItsDocumentedStatus() {
        assertThat(ErrorCode.VALIDATION_ERROR.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.UNAUTHORIZED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.CONFLICT.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.AI_UNAVAILABLE.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ErrorCode.AI_INVALID_RESPONSE.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ErrorCode.AI_NOT_CONFIGURED.getStatus()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(ErrorCode.IMPORT_FAILED.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.INTERNAL_ERROR.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("Không có mã lỗi nào ngoài bảng đã chốt")
    void containsExactlyTheDocumentedCodes() {
        assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name))
                .containsExactlyInAnyOrder(
                        "VALIDATION_ERROR", "UNAUTHORIZED", "NOT_FOUND", "CONFLICT",
                        "AI_UNAVAILABLE", "AI_INVALID_RESPONSE", "AI_NOT_CONFIGURED",
                        "IMPORT_FAILED", "INTERNAL_ERROR");
    }
}
