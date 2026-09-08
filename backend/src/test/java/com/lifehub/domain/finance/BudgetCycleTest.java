package com.lifehub.domain.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T3-08, T3-09 — budget cycle boundaries. */
class BudgetCycleTest {

    @Test
    @DisplayName("T3-08 — giao dịch ngày cuối tháng rơi đúng vào chu kỳ MONTHLY của tháng đó")
    void monthlyCycleCoversTheWholeCalendarMonth() {
        BudgetCycle cycle = Budget.cycleFor(LocalDate.of(2026, 9, 30), BudgetPeriod.MONTHLY);

        assertThat(cycle.start()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(cycle.endExclusive()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(cycle.endInclusive()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(cycle.contains(LocalDate.of(2026, 9, 30))).isTrue();
        assertThat(cycle.contains(LocalDate.of(2026, 10, 1))).isFalse();
    }

    @Test
    @DisplayName("Tháng 2 năm nhuận vẫn ra đúng 29 ngày")
    void handlesLeapFebruary() {
        BudgetCycle cycle = Budget.cycleFor(LocalDate.of(2028, 2, 15), BudgetPeriod.MONTHLY);

        assertThat(cycle.start()).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(cycle.endInclusive()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    @DisplayName("T3-09 — chu kỳ WEEKLY bắt đầu thứ 2 và kết thúc chủ nhật")
    void weeklyCycleRunsMondayToSunday() {
        // 2026-09-08 is a Tuesday.
        BudgetCycle cycle = Budget.cycleFor(LocalDate.of(2026, 9, 8), BudgetPeriod.WEEKLY);

        assertThat(cycle.start()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(cycle.endInclusive()).isEqualTo(LocalDate.of(2026, 9, 13));
    }

    @Test
    @DisplayName("Biên chu kỳ WEEKLY: chủ nhật thuộc tuần cũ, thứ 2 kế tiếp thuộc tuần mới")
    void weeklyBoundariesDoNotOverlap() {
        BudgetCycle sunday = Budget.cycleFor(LocalDate.of(2026, 9, 13), BudgetPeriod.WEEKLY);
        BudgetCycle monday = Budget.cycleFor(LocalDate.of(2026, 9, 14), BudgetPeriod.WEEKLY);

        assertThat(sunday.start()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(monday.start()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(sunday.endExclusive())
                .as("chu kỳ liền nhau phải khít, không chồng lấn và không hở")
                .isEqualTo(monday.start());
    }

    @Test
    @DisplayName("Chu kỳ YEARLY chạy từ 1/1 tới 31/12")
    void yearlyCycleCoversTheCalendarYear() {
        BudgetCycle cycle = Budget.cycleFor(LocalDate.of(2026, 7, 4), BudgetPeriod.YEARLY);

        assertThat(cycle.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(cycle.endInclusive()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("Ngân sách chưa có hiệu lực trước ngày bắt đầu")
    void isNotActiveBeforeItsStartDate() {
        Category category = expenseCategory();
        Budget budget = new Budget(
                category, Money.of(3_000_000L), BudgetPeriod.MONTHLY, LocalDate.of(2026, 9, 10));

        assertThat(budget.isActiveOn(LocalDate.of(2026, 9, 9))).isFalse();
        assertThat(budget.isActiveOn(LocalDate.of(2026, 9, 10))).isTrue();
    }

    private Category expenseCategory() {
        return new Category("Ăn uống", CategoryType.EXPENSE, null, "UtensilsCrossed", "#f59e0b");
    }
}
