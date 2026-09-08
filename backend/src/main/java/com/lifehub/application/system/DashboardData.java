package com.lifehub.application.system;

import com.lifehub.domain.finance.BudgetAlert;
import java.util.List;

/**
 * The at-a-glance numbers on the home screen (FR-SYS-01, 06-API-SPEC.md 2).
 *
 * <p>Delivered inside {@code GET /bootstrap} rather than from an endpoint of its own, because the
 * whole point of the bootstrap call is that the renderer gets everything it needs to draw the first
 * screen in one round trip.
 *
 * @param todayTasks live tasks due today that are not finished
 * @param overdueTasks live tasks whose deadline has already passed (FR-TSK-12)
 * @param upcomingEvents event instances starting in the next seven days
 * @param monthExpense total spent this calendar month, in đồng
 * @param monthIncome total received this calendar month, in đồng
 */
public record DashboardData(
        long todayTasks,
        long overdueTasks,
        long upcomingEvents,
        long monthExpense,
        long monthIncome,
        List<BudgetAlert> budgetAlerts) {
}
