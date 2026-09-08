package com.lifehub.application.finance;

import com.lifehub.application.finance.FinanceCommands.CreateBudget;
import com.lifehub.application.finance.FinanceCommands.UpdateBudget;
import com.lifehub.domain.common.ConflictException;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.finance.Budget;
import com.lifehub.domain.finance.BudgetAlert;
import com.lifehub.domain.finance.BudgetCycle;
import com.lifehub.domain.finance.BudgetPeriod;
import com.lifehub.domain.finance.BudgetRepository;
import com.lifehub.domain.finance.Category;
import com.lifehub.domain.finance.CategoryRepository;
import com.lifehub.domain.finance.Money;
import com.lifehub.domain.finance.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budget management and threshold evaluation (FR-FIN-08, FR-FIN-09).
 *
 * <p>Spending is attributed to a budget through the category tree: a coffee logged under
 * "Ăn uống › Cà phê" counts against a budget set on "Ăn uống". Without that, a user who takes the
 * trouble to use sub-categories would find their parent budgets permanently reading zero.
 */
@Service
@Transactional
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;
    private final ZoneId displayZone;

    public BudgetService(
            BudgetRepository budgetRepository,
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            Clock clock,
            ZoneId displayZone) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
        this.displayZone = displayZone;
    }

    @Transactional(readOnly = true)
    public List<BudgetStatus> findAll(boolean includeInactive) {
        LocalDate today = LocalDate.now(clock.withZone(displayZone));
        return budgetRepository.findAll(includeInactive).stream()
                .map(budget -> statusOf(budget, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public BudgetStatus findById(String id) {
        return statusOf(require(id), LocalDate.now(clock.withZone(displayZone)));
    }

    public BudgetStatus create(CreateBudget command) {
        Category category = requireCategory(command.categoryId());
        BudgetPeriod period = command.period() == null ? BudgetPeriod.MONTHLY : command.period();
        requireNoDuplicate(category.getId(), period, null);

        Budget budget = new Budget(
                category,
                Money.of(command.limitAmount()),
                period,
                command.startDate() == null ? LocalDate.now(clock.withZone(displayZone)) : command.startDate());
        if (command.isActive() != null) {
            budget.activate(command.isActive());
        }

        return statusOf(budgetRepository.save(budget), LocalDate.now(clock.withZone(displayZone)));
    }

    public BudgetStatus update(String id, UpdateBudget command) {
        Budget budget = require(id);

        command.categoryId().ifPresent(categoryId -> budget.changeCategory(requireCategory(categoryId)));
        command.limitAmount().ifPresent(limit -> budget.changeLimit(Money.of(limit)));
        command.period().ifPresent(budget::changePeriod);
        command.startDate().ifPresent(budget::changeStartDate);
        command.isActive().ifPresent(active -> budget.activate(Boolean.TRUE.equals(active)));

        if (budget.isActive()) {
            requireNoDuplicate(budget.getCategoryId(), budget.getPeriod(), id);
        }

        return statusOf(budgetRepository.save(budget), LocalDate.now(clock.withZone(displayZone)));
    }

    public void delete(String id) {
        Budget budget = require(id);
        budget.activate(false);
        budget.softDelete(clock.instant());
        budgetRepository.save(budget);
    }

    /**
     * The strongest alert triggered by spending in {@code categoryId} at {@code occurredAt}
     * (SD-01 step 55-67).
     *
     * <p>Returns null below 80%, which is the signal the controller uses to omit the field
     * entirely. A transaction can sit under both a child and a parent budget; the more urgent of
     * the two is what the user needs to see first.
     */
    @Transactional(readOnly = true)
    public BudgetAlert alertFor(String categoryId, Instant occurredAt) {
        if (categoryId == null) {
            return null;
        }
        LocalDate date = LocalDate.ofInstant(
                occurredAt == null ? clock.instant() : occurredAt, displayZone);

        return budgetsCovering(categoryId).stream()
                .filter(budget -> budget.isActiveOn(date))
                .map(budget -> statusOf(budget, date))
                .map(BudgetStatus::toAlert)
                .filter(alert -> alert != null)
                .max(java.util.Comparator.comparingDouble(BudgetAlert::usage))
                .orElse(null);
    }

    /** Every alert currently firing, for the dashboard summary (FR-SYS-01). */
    @Transactional(readOnly = true)
    public List<BudgetAlert> currentAlerts() {
        LocalDate today = LocalDate.now(clock.withZone(displayZone));
        return budgetRepository.findAll(false).stream()
                .filter(budget -> budget.isActiveOn(today))
                .map(budget -> statusOf(budget, today))
                .map(BudgetStatus::toAlert)
                .filter(alert -> alert != null)
                .toList();
    }

    /** Usage of one budget over the cycle containing {@code date}. */
    public BudgetStatus statusOf(Budget budget, LocalDate date) {
        hydrate(budget);
        BudgetCycle cycle = budget.cycleFor(date);
        long spent = transactionRepository.sumExpense(
                categoryScopeOf(budget.getCategoryId()),
                cycle.startInstant(displayZone),
                cycle.endInstant(displayZone));
        return BudgetStatus.of(budget, cycle, Money.of(Math.max(spent, 0L)));
    }

    /**
     * Loads the category and its parent while the session is still open.
     *
     * <p>{@code open-in-view} is off, so anything the mapper will read has to be touched before
     * this transaction ends. The parent is the easy one to miss: the response renders a
     * sub-category as "Ăn uống › Cà phê", so a budget on any child category needs the parent name,
     * and a budget on a root category never exercises that path. Every public method here funnels
     * through this one, which is why the hydration lives here and not in each of them.
     *
     * <p>Mirrors {@code TransactionWriter.hydrate}, for the same reason.
     */
    private void hydrate(Budget budget) {
        Category category = budget.getCategory();
        if (category == null) {
            return;
        }
        category.getName();
        Category parent = category.getParent();
        if (parent != null) {
            parent.getName();
        }
    }

    /** A budget's category plus its children, since a parent budget covers its whole subtree. */
    private List<String> categoryScopeOf(String categoryId) {
        List<String> ids = new ArrayList<>();
        ids.add(categoryId);
        categoryRepository.findChildren(categoryId).forEach(child -> ids.add(child.getId()));
        return ids;
    }

    /** Budgets that a transaction in {@code categoryId} counts against: its own and its parent's. */
    private List<Budget> budgetsCovering(String categoryId) {
        List<String> ids = new ArrayList<>();
        ids.add(categoryId);
        categoryRepository
                .findById(categoryId)
                .map(Category::getParentId)
                .ifPresent(ids::add);
        return budgetRepository.findActiveByCategoryIds(ids);
    }

    private Budget require(String id) {
        return budgetRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ngân sách"));
    }

    private Category requireCategory(String categoryId) {
        return categoryRepository
                .findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));
    }

    private void requireNoDuplicate(String categoryId, BudgetPeriod period, String excludingId) {
        if (budgetRepository.existsForCategoryAndPeriod(categoryId, period, excludingId)) {
            throw new ConflictException("Danh mục này đã có ngân sách cùng chu kỳ", "categoryId");
        }
    }
}
