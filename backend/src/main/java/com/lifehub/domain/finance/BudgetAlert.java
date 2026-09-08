package com.lifehub.domain.finance;

/**
 * How close a category is to its budget limit (FR-FIN-09).
 *
 * <p>Only ever produced at or above the 80% mark - below that there is nothing to warn about and
 * the budget service returns null instead.
 *
 * @param usage spent divided by limit, which can exceed 1.0
 */
public record BudgetAlert(
        String budgetId,
        String categoryId,
        String categoryName,
        double usage,
        BudgetAlertLevel level,
        long limitAmount,
        long spentAmount) {

    public static final double WARNING_THRESHOLD = 0.8;
    public static final double EXCEEDED_THRESHOLD = 1.0;

    /** The level for a usage ratio, or null when the user is comfortably inside the limit. */
    public static BudgetAlertLevel levelFor(double usage) {
        if (usage >= EXCEEDED_THRESHOLD) {
            return BudgetAlertLevel.EXCEEDED;
        }
        if (usage >= WARNING_THRESHOLD) {
            return BudgetAlertLevel.WARNING;
        }
        return null;
    }
}
