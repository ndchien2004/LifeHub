package com.lifehub.domain.ai;

/** Kind of AI call, as stored in {@code ai_parse_log.request_type} (03-DATA-MODEL.md 2.10). */
public enum AiRequestType {
    NL_PARSE,
    CATEGORY_SUGGEST,
    /** Phase 5 (FR-AI-11); the column already accepts it so no migration is needed then. */
    WEEKLY_INSIGHT
}
