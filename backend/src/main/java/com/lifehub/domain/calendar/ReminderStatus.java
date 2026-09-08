package com.lifehub.domain.calendar;

/** Lifecycle of a reminder (03-DATA-MODEL.md §3.2). */
public enum ReminderStatus {

    /** Waiting for its trigger time. */
    PENDING,
    /** The scheduler pushed a notification for it. */
    FIRED,
    /** The user postponed it; a fresh PENDING reminder was created alongside. */
    SNOOZED,
    /** The user acknowledged it. */
    DISMISSED,
    /** Its trigger time passed by more than 24 hours while the app was closed. */
    EXPIRED;

    /** Whether the scheduler should still consider firing it. */
    public boolean isPending() {
        return this == PENDING;
    }

    /** Whether the reminder has reached a state the user can no longer act on. */
    public boolean isClosed() {
        return this == DISMISSED || this == EXPIRED;
    }
}
