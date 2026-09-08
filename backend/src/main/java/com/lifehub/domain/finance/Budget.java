package com.lifehub.domain.finance;

import com.lifehub.domain.common.BaseEntity;
import com.lifehub.domain.common.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * A spending limit for one category over a repeating period (FR-FIN-08, FR-FIN-09).
 *
 * <p>Cycles are aligned to the calendar rather than stepped from {@code startDate}: a weekly budget
 * runs Monday to Sunday, a monthly one runs the 1st to the end of the month, a yearly one runs
 * January to December. A budget created on the 17th otherwise reports "chu kỳ này" for a window
 * ending on the 16th of next month, which matches no statement, no salary date and no mental model
 * the user has. {@code startDate} keeps its meaning as the date the limit takes effect
 * (see {@link #isActiveOn}); it just does not shift the grid. Monday as the week boundary follows
 * the {@code app.week_start} default in 03-DATA-MODEL.md 2.11.
 */
@Entity
@Table(name = "budget")
public class Budget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Stored as a plain long for the same reason as {@code MoneyTransaction.amount}. */
    @Column(name = "limit_amount", nullable = false)
    private long limitAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false)
    private BudgetPeriod period = BudgetPeriod.MONTHLY;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Budget() {
    }

    public Budget(Category category, Money limitAmount, BudgetPeriod period, LocalDate startDate) {
        changeCategory(category);
        changeLimit(limitAmount);
        changePeriod(period);
        changeStartDate(startDate);
    }

    public void changeCategory(Category category) {
        if (category == null) {
            throw new ValidationException("Ngân sách phải gắn với một danh mục", "categoryId");
        }
        if (category.getType() != CategoryType.EXPENSE) {
            throw new ValidationException("Chỉ đặt được ngân sách cho danh mục chi", "categoryId");
        }
        this.category = category;
    }

    public void changeLimit(Money limitAmount) {
        if (limitAmount == null || !limitAmount.isPositive()) {
            throw new ValidationException("Hạn mức phải lớn hơn 0", "limitAmount");
        }
        this.limitAmount = limitAmount.toLong();
    }

    public void changePeriod(BudgetPeriod period) {
        if (period != null) {
            this.period = period;
        }
    }

    public void changeStartDate(LocalDate startDate) {
        if (startDate == null) {
            throw new ValidationException("Ngày bắt đầu không được để trống", "startDate");
        }
        this.startDate = startDate;
    }

    public void activate(boolean active) {
        this.active = active;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** The cycle containing {@code date}, aligned to the calendar (see the class comment). */
    public BudgetCycle cycleFor(LocalDate date) {
        return cycleFor(date, period);
    }

    /** Exposed statically so the calculation can be unit tested without building an entity. */
    public static BudgetCycle cycleFor(LocalDate date, BudgetPeriod period) {
        return switch (period) {
            case WEEKLY -> {
                LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new BudgetCycle(start, start.plusWeeks(1));
            }
            case MONTHLY -> {
                LocalDate start = date.withDayOfMonth(1);
                yield new BudgetCycle(start, start.plusMonths(1));
            }
            case YEARLY -> {
                LocalDate start = date.withDayOfYear(1);
                yield new BudgetCycle(start, start.plusYears(1));
            }
        };
    }

    /** A budget does not police spending that happened before the user set it up. */
    public boolean isActiveOn(LocalDate date) {
        return active && !isDeleted() && !date.isBefore(startDate);
    }

    public Category getCategory() {
        return category;
    }

    public String getCategoryId() {
        return category == null ? null : category.getId();
    }

    public Money getLimitAmount() {
        return Money.of(limitAmount);
    }

    public BudgetPeriod getPeriod() {
        return period;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
