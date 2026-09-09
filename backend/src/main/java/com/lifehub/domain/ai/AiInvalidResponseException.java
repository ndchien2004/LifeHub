package com.lifehub.domain.ai;

/**
 * The model answered, but not with JSON matching the schema in 04-ARCHITECTURE.md 7.
 *
 * <p>Covers both halves of that check: text that is not JSON at all after sanitising (T4-03), and
 * well formed JSON missing a required field (T4-16). The caller retries once with a stricter prompt
 * before giving up (SD-02).
 */
public class AiInvalidResponseException extends AiException {

    public AiInvalidResponseException(String message) {
        super(AiErrorCode.INVALID_JSON, message);
    }

    public AiInvalidResponseException(String message, Throwable cause) {
        super(AiErrorCode.INVALID_JSON, message, cause);
    }
}
