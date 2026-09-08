package com.lifehub.application.finance;

import java.util.List;

/**
 * Aggregated totals behind the pie and line charts (FR-FIN-10, FR-FIN-11).
 *
 * <p>Transfers are excluded from every figure here. Moving money between two of your own wallets
 * is not income and not expense; counting it would inflate both totals and make the pie chart
 * disagree with the transaction list it sits next to.
 *
 * @param net income minus expense, which is negative in a month the user overspent
 */
public record TransactionSummary(
        long totalIncome, long totalExpense, long net, List<SummaryGroup> groups) {

    public static TransactionSummary of(long totalIncome, long totalExpense, List<SummaryGroup> groups) {
        return new TransactionSummary(totalIncome, totalExpense, totalIncome - totalExpense, groups);
    }

    /**
     * One slice or point.
     *
     * @param key category id, wallet id or ISO date depending on the grouping
     * @param label what the chart legend shows
     * @param percentage share of the grouped total, 0-100, rounded to one decimal
     */
    public record SummaryGroup(
            String key,
            String label,
            String color,
            long amount,
            double percentage,
            long transactionCount) {
    }
}
