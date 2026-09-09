package com.lifehub.domain.ai;

/**
 * Base type for every way an AI call can fail.
 *
 * <p>Deliberately not a {@code DomainException}: those map to 4xx codes for a caller who sent bad
 * input, whereas these describe a service the user has no control over. Each carries an
 * {@link AiErrorCode} so the same value can go into the log row, the fallback warning, and the HTTP
 * error code without being re-derived at each step.
 */
public abstract class AiException extends RuntimeException {

    private final AiErrorCode errorCode;

    protected AiException(AiErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    protected AiException(AiErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public AiErrorCode getErrorCode() {
        return errorCode;
    }
}
