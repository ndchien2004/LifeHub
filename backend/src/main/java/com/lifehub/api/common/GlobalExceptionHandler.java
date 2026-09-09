package com.lifehub.api.common;

import com.lifehub.domain.ai.AiInvalidResponseException;
import com.lifehub.domain.ai.AiNotConfiguredException;
import com.lifehub.domain.ai.AiUnavailableException;
import com.lifehub.domain.common.ConflictException;
import com.lifehub.domain.common.DomainException;
import com.lifehub.domain.common.IdGenerator;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.common.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Translates every exception into the error envelope from 04-ARCHITECTURE.md 8.
 *
 * <p>Two rules drive this class. A stack trace is never sent to the client (NFR-USE-03) - it goes to
 * the log file under a {@code traceId} that is also returned, so a user can quote the id from a
 * toast and the matching log line can be found. And the mapping from exception type to
 * {@link ErrorCode} lives here alone, which is what keeps the domain layer free of HTTP concepts.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(ValidationException ex) {
        return build(ErrorCode.VALIDATION_ERROR, ex.getMessage(), ex.getField(), ex, false);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return build(ErrorCode.NOT_FOUND, ex.getMessage(), null, ex, false);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return build(ErrorCode.CONFLICT, ex.getMessage(), ex.getField(), ex, false);
    }

    /** Fallback for any domain exception subtype added later without its own handler. */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex) {
        return build(ErrorCode.VALIDATION_ERROR, ex.getMessage(), ex.getField(), ex, false);
    }

    /**
     * No API key, on an endpoint that cannot answer without one.
     *
     * <p>428 rather than 401: the request was authenticated to this application perfectly well, it
     * is the AI provider credential that is missing, and the UI reacts by pointing at the Settings
     * screen instead of at a login (04-ARCHITECTURE.md 8).
     */
    @ExceptionHandler(AiNotConfiguredException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiNotConfigured(AiNotConfiguredException ex) {
        return build(ErrorCode.AI_NOT_CONFIGURED, ex.getMessage(), null, ex, false);
    }

    /**
     * The provider answered with something that was not the agreed JSON.
     *
     * <p>Rarely reaches here: {@code NlParseService} retries once and then falls back to the rule
     * based parser, so a user typing into the command palette sees a result rather than this error.
     * It surfaces for callers that have no fallback of their own.
     */
    @ExceptionHandler(AiInvalidResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiInvalidResponse(AiInvalidResponseException ex) {
        return build(ErrorCode.AI_INVALID_RESPONSE, ex.getMessage(), null, ex, false);
    }

    /**
     * The provider could not be reached, refused the key, or throttled.
     *
     * <p>The message is already written for the user and carries no provider detail, so a wrong key
     * reads as "API key không hợp lệ" rather than as a stack trace (NFR-USE-03).
     */
    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiUnavailable(AiUnavailableException ex) {
        return build(ErrorCode.AI_UNAVAILABLE, ex.getMessage(), null, ex, false);
    }

    /** Bean Validation failure on a request DTO. Reports the first offending field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBeanValidation(MethodArgumentNotValidException ex) {
        FieldError first = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = first != null ? first.getDefaultMessage() : "Dữ liệu không hợp lệ";
        String field = first != null ? first.getField() : null;
        return build(ErrorCode.VALIDATION_ERROR, message, field, ex, false);
    }

    /**
     * Body that Jackson could not read at all: malformed JSON, a bad enum value, an unparseable
     * timestamp, or a non UTF-8 payload.
     *
     * <p>This is the caller getting the request wrong, so it belongs in the 400 family. Without
     * this handler it falls through to the catch-all and is reported as an internal server error,
     * which sends the user looking for a fault that is not there.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(
                ErrorCode.VALIDATION_ERROR,
                "Dữ liệu gửi lên không đọc được. Kiểm tra lại định dạng JSON và mã hóa UTF-8.",
                null,
                ex,
                false);
    }

    /** A query parameter or path variable that could not be converted to its declared type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(
                ErrorCode.VALIDATION_ERROR,
                "Giá trị không hợp lệ cho tham số " + ex.getName(),
                ex.getName(),
                ex,
                false);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException ex) {
        return build(ErrorCode.NOT_FOUND, "Không tìm thấy đường dẫn yêu cầu", null, ex, false);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = IdGenerator.newId();
        log.error("Unhandled exception on {} {} [traceId={}]",
                request.getMethod(), request.getRequestURI(), traceId, ex);
        ApiError error = ApiError.of(ErrorCode.INTERNAL_ERROR,
                "Đã xảy ra lỗi không mong muốn. Vui lòng thử lại, nếu vẫn lỗi hãy gửi mã lỗi này kèm file log.",
                traceId);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()).body(ApiResponse.fail(error));
    }

    private ResponseEntity<ApiResponse<Void>> build(
            ErrorCode code, String message, String field, Exception ex, boolean logStackTrace) {
        String traceId = IdGenerator.newId();
        if (logStackTrace) {
            log.warn("{} [traceId={}]", code, traceId, ex);
        } else {
            log.warn("{}: {} [traceId={}]", code, message, traceId);
        }
        ApiError error = ApiError.of(code, message, field, traceId);
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.fail(error));
    }
}
