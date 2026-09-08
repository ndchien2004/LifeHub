package com.lifehub.domain.finance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * One closed budget period, as a half-open date range {@code [start, endExclusive)}.
 *
 * <p>Half-open rather than inclusive on both ends because consecutive cycles then tile the
 * timeline exactly: the last instant of September and the first of October belong to exactly one
 * cycle each, with no gap and no overlap at midnight.
 *
 * @param start first day of the cycle, inclusive
 * @param endExclusive first day of the next cycle
 */
public record BudgetCycle(LocalDate start, LocalDate endExclusive) {

    /** Cycle boundary as a UTC instant, for comparing against stored {@code occurred_at} values. */
    public Instant startInstant(ZoneId zone) {
        return start.atStartOfDay(zone).toInstant();
    }

    public Instant endInstant(ZoneId zone) {
        return endExclusive.atStartOfDay(zone).toInstant();
    }

    /** Last day the user actually sees, which is one day before the exclusive end. */
    public LocalDate endInclusive() {
        return endExclusive.minusDays(1);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && date.isBefore(endExclusive);
    }
}
