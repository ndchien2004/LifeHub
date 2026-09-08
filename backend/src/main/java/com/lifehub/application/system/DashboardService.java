package com.lifehub.application.system;

import com.lifehub.application.calendar.EventQueryService;
import com.lifehub.application.finance.BudgetService;
import com.lifehub.application.finance.TransactionQueryService;
import com.lifehub.domain.common.PageRequest;
import com.lifehub.domain.finance.TransactionType;
import com.lifehub.domain.task.TaskFilter;
import com.lifehub.domain.task.TaskRepository;
import com.lifehub.domain.task.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the dashboard figures from the three business modules (FR-SYS-01).
 *
 * <p>Every window here is a <em>local</em> calendar window, not a rolling 24 hours or 30 days:
 * "hôm nay" ends at midnight in {@code Asia/Ho_Chi_Minh} and "tháng này" means the calendar month,
 * which is what the user compares against a bank statement.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int UPCOMING_DAYS = 7;

    private final TaskRepository taskRepository;
    private final EventQueryService eventQueryService;
    private final TransactionQueryService transactionQueryService;
    private final BudgetService budgetService;
    private final Clock clock;
    private final ZoneId displayZone;

    public DashboardService(
            TaskRepository taskRepository,
            EventQueryService eventQueryService,
            TransactionQueryService transactionQueryService,
            BudgetService budgetService,
            Clock clock,
            ZoneId displayZone) {
        this.taskRepository = taskRepository;
        this.eventQueryService = eventQueryService;
        this.transactionQueryService = transactionQueryService;
        this.budgetService = budgetService;
        this.clock = clock;
        this.displayZone = displayZone;
    }

    public DashboardData load() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, displayZone);

        Instant startOfToday = today.atStartOfDay(displayZone).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(displayZone).toInstant();
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(displayZone).toInstant();
        Instant startOfNextMonth =
                today.withDayOfMonth(1).plusMonths(1).atStartOfDay(displayZone).toInstant();

        return new DashboardData(
                countTasks(startOfToday, startOfTomorrow.minusMillis(1)),
                countTasks(null, now),
                countUpcomingEvents(now, today.plusDays(UPCOMING_DAYS).atStartOfDay(displayZone).toInstant()),
                transactionQueryService.sumByType(TransactionType.EXPENSE, startOfMonth, startOfNextMonth),
                transactionQueryService.sumByType(TransactionType.INCOME, startOfMonth, startOfNextMonth),
                budgetService.currentAlerts());
    }

    /**
     * Unfinished tasks with a deadline inside a window.
     *
     * <p>Counted through the same filter the task list uses, so the number on the dashboard can
     * never disagree with what the user sees after clicking through to it.
     */
    private long countTasks(Instant from, Instant to) {
        TaskFilter filter = new TaskFilter(
                null,
                List.of(),
                List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS),
                List.of(),
                from,
                to,
                null,
                false,
                false);
        return taskRepository.search(filter, PageRequest.of(0, 1)).totalItems();
    }

    private long countUpcomingEvents(Instant from, Instant to) {
        return eventQueryService.findInRange(from, to, false).occurrences().size();
    }
}
