package com.lifehub.application.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lifehub.domain.finance.Budget;
import com.lifehub.domain.finance.BudgetAlert;
import com.lifehub.domain.finance.BudgetAlertLevel;
import com.lifehub.domain.finance.BudgetPeriod;
import com.lifehub.domain.finance.BudgetRepository;
import com.lifehub.domain.finance.Category;
import com.lifehub.domain.finance.CategoryRepository;
import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.Money;
import com.lifehub.domain.finance.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** T3-10 — where the yellow and red budget warnings start. */
class BudgetServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-09-08T05:00:00Z");
    private static final long LIMIT = 3_000_000L;

    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);

    private final BudgetService service = new BudgetService(
            budgetRepository,
            categoryRepository,
            transactionRepository,
            Clock.fixed(NOW, ZoneId.of("UTC")),
            ZONE);

    private final Category food = new Category("Ăn uống", CategoryType.EXPENSE, null, null, "#f59e0b");

    @ParameterizedTest(name = "usage {0} -> {1}")
    @CsvSource({
        "0.79, NONE",
        "0.80, WARNING",
        "0.99, WARNING",
        "1.00, EXCEEDED",
        "1.50, EXCEEDED",
    })
    @DisplayName("T3-10 — ngưỡng cảnh báo: dưới 80% không cảnh báo, từ 80% vàng, từ 100% đỏ")
    void mapsUsageToTheRightLevel(double usage, String expected) {
        long spent = Math.round(LIMIT * usage);
        BudgetStatus status = BudgetStatus.of(budget(), cycle(), Money.of(spent));

        BudgetAlertLevel level = status.level();

        if ("NONE".equals(expected)) {
            assertThat(level).isNull();
            assertThat(status.toAlert()).as("không có cảnh báo thì không có payload").isNull();
        } else {
            assertThat(level).isEqualTo(BudgetAlertLevel.valueOf(expected));
            assertThat(status.toAlert().level()).isEqualTo(level);
        }
    }

    @Test
    @DisplayName("Chi tiêu ở danh mục con vẫn tính vào ngân sách của danh mục cha")
    void countsChildCategorySpendingTowardsTheParentBudget() {
        Category coffee = new Category("Cà phê", CategoryType.EXPENSE, food, null, "#f59e0b");

        when(categoryRepository.findById(coffee.getId())).thenReturn(Optional.of(coffee));
        when(budgetRepository.findActiveByCategoryIds(anyList())).thenReturn(List.of(budget()));
        when(categoryRepository.findChildren(food.getId())).thenReturn(List.of(coffee));
        when(transactionRepository.sumExpense(anyList(), any(), any())).thenReturn(2_700_000L);

        BudgetAlert alert = service.alertFor(coffee.getId(), NOW);

        assertThat(alert).isNotNull();
        assertThat(alert.level()).isEqualTo(BudgetAlertLevel.WARNING);
        assertThat(alert.spentAmount()).isEqualTo(2_700_000L);
        assertThat(alert.usage()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("Dưới ngưỡng thì không trả cảnh báo — SD-01 chờ null ở nhánh này")
    void returnsNullBelowTheWarningThreshold() {
        when(categoryRepository.findById(food.getId())).thenReturn(Optional.of(food));
        when(budgetRepository.findActiveByCategoryIds(anyList())).thenReturn(List.of(budget()));
        when(categoryRepository.findChildren(food.getId())).thenReturn(List.of());
        when(transactionRepository.sumExpense(anyList(), any(), any())).thenReturn(1_000_000L);

        assertThat(service.alertFor(food.getId(), NOW)).isNull();
    }

    @Test
    @DisplayName("Ngân sách chưa tới ngày bắt đầu thì không cảnh báo")
    void ignoresBudgetsThatHaveNotStartedYet() {
        Budget future = new Budget(
                food, Money.of(LIMIT), BudgetPeriod.MONTHLY, LocalDate.of(2027, 1, 1));

        when(categoryRepository.findById(food.getId())).thenReturn(Optional.of(food));
        when(budgetRepository.findActiveByCategoryIds(anyList())).thenReturn(List.of(future));
        when(transactionRepository.sumExpense(anyList(), any(), any())).thenReturn(5_000_000L);

        assertThat(service.alertFor(food.getId(), NOW)).isNull();
    }

    @Test
    @DisplayName("Chi vượt hạn mức thì phần còn lại là 0, không phải số âm")
    void clampsRemainingAtZero() {
        BudgetStatus status = BudgetStatus.of(budget(), cycle(), Money.of(4_500_000L));

        assertThat(status.remaining()).isEqualTo(Money.ZERO);
        assertThat(status.usage()).isEqualTo(1.5);
    }

    private Budget budget() {
        return new Budget(food, Money.of(LIMIT), BudgetPeriod.MONTHLY, LocalDate.of(2026, 1, 1));
    }

    private com.lifehub.domain.finance.BudgetCycle cycle() {
        return Budget.cycleFor(LocalDate.of(2026, 9, 8), BudgetPeriod.MONTHLY);
    }
}
