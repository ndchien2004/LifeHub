package com.lifehub.domain.calendar;

/**
 * How far an edit to one occurrence of a repeating series reaches (FR-CAL-04, SD-05).
 */
public enum EditScope {

    /** Writes an {@code event_exception} row; every other occurrence keeps the master values. */
    THIS_ONLY,
    /** Closes the master with an UNTIL and starts a new series from this occurrence. */
    THIS_AND_FOLLOWING,
    /** Updates the master row, so the whole series changes. */
    ALL
}
