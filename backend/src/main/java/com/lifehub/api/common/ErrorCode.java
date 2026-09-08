package com.lifehub.api.common;

import org.springframework.http.HttpStatus;

/**
 * The complete error vocabulary of the REST API (04-ARCHITECTURE.md 8).
 *
 * <p>Every error response carries exactly one of these codes. The frontend switches on the code, so
 * codes are part of the API contract and must not be renamed once released.
 */
public enum ErrorCode {

    /** Input failed validation. */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    /** Missing or wrong {@code X-App-Token} header. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    /** No such record, or it has been soft deleted. */
    NOT_FOUND(HttpStatus.NOT_FOUND),
    /** Business constraint violated, for example a duplicate name. */
    CONFLICT(HttpStatus.CONFLICT),
    /** AI provider unreachable; the request already fell back to the rule based parser. */
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    /** AI responded, but not with JSON matching the agreed schema. */
    AI_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY),
    /** No API key configured yet. */
    AI_NOT_CONFIGURED(HttpStatus.PRECONDITION_REQUIRED),
    /** CSV import could not be completed; nothing was written. */
    IMPORT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    /** Anything unhandled. Details go to the log, never to the client. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
