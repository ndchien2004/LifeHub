package com.lifehub.domain.finance;

import java.io.Serializable;
import java.util.Objects;

/**
 * A non-negative amount of money, always a whole number of đồng (FR-FIN-06).
 *
 * <p>Deliberately has no constructor or factory that accepts {@code double} or {@code float}.
 * Binary floating point cannot represent most decimal fractions exactly, so a running total built
 * from doubles drifts; in a ledger a one đồng drift is simply a wrong number. Keeping the only door
 * into this type a {@code long} means the error can never enter the system in the first place, and
 * 08-TEST-PLAN.md T3-02 asserts that door stays shut by reflection.
 *
 * <p>Amounts are non-negative because every stored amount is a magnitude: direction is carried by
 * {@link TransactionType}, not by the sign. Balances, which genuinely can go negative on a credit
 * wallet, are plain {@code long} and never a {@code Money}.
 */
public final class Money implements Comparable<Money>, Serializable {

    public static final Money ZERO = new Money(0L);

    private final long dong;

    private Money(long dong) {
        if (dong < 0) {
            throw new IllegalArgumentException("Số tiền không được âm: " + dong);
        }
        this.dong = dong;
    }

    /** The only way to build a {@code Money}. */
    public static Money of(long dong) {
        return dong == 0L ? ZERO : new Money(dong);
    }

    /** Reads a nullable stored value, treating a missing amount as zero. */
    public static Money ofNullable(Long dong) {
        return dong == null ? ZERO : of(dong);
    }

    public long toLong() {
        return dong;
    }

    public boolean isZero() {
        return dong == 0L;
    }

    public boolean isPositive() {
        return dong > 0L;
    }

    public Money plus(Money other) {
        return of(Math.addExact(dong, other.dong));
    }

    /** Subtraction that refuses to go negative, since a {@code Money} never can. */
    public Money minus(Money other) {
        return of(Math.subtractExact(dong, other.dong));
    }

    public Money times(long factor) {
        return of(Math.multiplyExact(dong, factor));
    }

    /**
     * This amount as a fraction of {@code total}, for budget usage.
     *
     * <p>The result is a ratio for display and threshold comparison only - it never flows back into
     * a stored amount. A zero limit reads as fully used rather than as division by zero.
     */
    public double ratioTo(Money total) {
        if (total == null || total.isZero()) {
            return isZero() ? 0.0 : 1.0;
        }
        return (double) dong / (double) total.dong;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(dong, other.dong);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Money money && dong == money.dong;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(dong);
    }

    @Override
    public String toString() {
        return dong + " đ";
    }
}
