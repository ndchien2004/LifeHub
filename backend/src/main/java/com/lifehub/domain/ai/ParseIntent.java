package com.lifehub.domain.ai;

/** What the user meant to create (FR-AI-02). */
public enum ParseIntent {
    TRANSACTION,
    TASK,
    EVENT,
    /** Nothing recognisable; the UI asks the user to pick a type (UC-09 alternate flow 9a). */
    UNKNOWN
}
