package com.lifehub.application.finance;

import com.lifehub.domain.finance.Budget;
import com.lifehub.domain.finance.BudgetAlert;
import com.lifehub.domain.finance.BudgetAlertLevel;
import com.lifehub.domain.finance.BudgetCycle;
import com.lifehub.domain.finance.Money;

/**
 * A budget with its current cycle and how much of it is used (FR-FIN-09).
 *
 * @param usage spent divided by limit; can exceed 1.0, which is exactly the case the red state
 *     exists to show
 * @param level null while usage is below 80%
 */
public record BudgetStatus(
        Budget budget, BudgetCycle cycle, Money spent, double usage, BudgetAlertLevel level) {

    public static BudgetStatus of(Budget budget, BudgetCycle cycle, Money spent) {
        double usage = spent.ratioTo(budget.getLimitAmount());
        return new BudgetStatus(budget, cycle, spent, usage, BudgetAlert.levelFor(usage));
    }

    /** The alert payload SD-01 returns alongside a newly written transaction, or null. */
    public BudgetAlert toAlert() {
        if (level == null) {
            return null;
        }
        return new BudgetAlert(
                budget.getId(),
                budget.getCategoryId(),
                budget.getCategory().getName(),
                usage,
                level,
                budget.getLimitAmount().toLong(),
                spent.toLong());
    }

    public Money remaining() {
        Money limit = budget.getLimitAmount();
        return spent.compareTo(limit) >= 0 ? Money.ZERO : limit.minus(spent);
    }
}
