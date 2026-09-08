package com.lifehub.domain.task;

/**
 * Task lifecycle states (FR-TSK-04), matching the state diagram in 03-DATA-MODEL.md §3.1.
 *
 * <p>Every transition between these states is legal, including reopening a finished task
 * (DONE → TODO) and restoring a cancelled one. The diagram permits all of them, and for a
 * single-user tool a state machine that refuses transitions would only get in the way.
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    CANCELLED;

    public boolean isDone() {
        return this == DONE;
    }
}
