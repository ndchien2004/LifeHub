package com.lifehub.domain.ai;

/**
 * No API key has been supplied, so a call that requires one cannot even be attempted.
 *
 * <p>Only raised by endpoints where the absence is the answer the caller asked about - notably
 * {@code POST /ai/test-connection}. {@code POST /ai/parse} never raises it: an unconfigured key is a
 * normal state that routes straight to the rule based parser instead (SD-02, first alt branch).
 */
public class AiNotConfiguredException extends AiException {

    public AiNotConfiguredException(String message) {
        super(AiErrorCode.AUTH, message);
    }
}
