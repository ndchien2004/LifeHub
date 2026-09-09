package com.lifehub.domain.ai;

/** Why an AI call failed, as stored in {@code ai_parse_log.error_code}. */
public enum AiErrorCode {
    /** No answer within the deadline, or the network was unreachable. */
    TIMEOUT,
    /** An answer arrived but was not JSON matching the agreed schema. */
    INVALID_JSON,
    /** The API key is missing, wrong, or revoked. */
    AUTH,
    /** The provider throttled or the account ran out of quota. */
    RATE_LIMIT,
    UNKNOWN
}
