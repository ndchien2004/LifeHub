package com.lifehub.domain.ai;

/**
 * The provider could not be reached, refused the key, or throttled the request.
 *
 * <p>Always recoverable from the user's point of view: the caller falls back to the rule based
 * parser and reports the reason as a warning (FR-AI-08).
 */
public class AiUnavailableException extends AiException {

    public AiUnavailableException(AiErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AiUnavailableException(AiErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
