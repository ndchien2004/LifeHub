package com.lifehub.infrastructure.scheduler;

import com.lifehub.application.finance.RecurringTransactionService;
import com.lifehub.domain.finance.MoneyTransaction;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires recurring transaction rules that have come due (FR-FIN-13).
 *
 * <p>Runs once at startup and then hourly. Startup matters more than the interval: this is a
 * desktop app that is closed most of the time, so the moment it opens is the realistic opportunity
 * to catch up on a rent payment that fell due while it was shut. The hourly tick then covers the
 * case of the app being left open across midnight.
 */
@Component
public class RecurringTransactionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionScheduler.class);
    private static final long HOURLY_MILLIS = 60L * 60L * 1000L;

    private final RecurringTransactionService recurringTransactionService;

    public RecurringTransactionScheduler(RecurringTransactionService recurringTransactionService) {
        this.recurringTransactionService = recurringTransactionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        generate();
    }

    @Scheduled(fixedRate = HOURLY_MILLIS, initialDelay = HOURLY_MILLIS)
    public void runPeriodically() {
        generate();
    }

    /** Returns the transactions created, so the behaviour is assertable in a test. */
    public List<MoneyTransaction> generate() {
        try {
            List<MoneyTransaction> created = recurringTransactionService.runDue();
            if (!created.isEmpty()) {
                log.info("Đã sinh {} giao dịch định kỳ", created.size());
            }
            return created;
        } catch (RuntimeException e) {
            // Never let this kill the scheduler thread: a failure now must not stop the next tick.
            log.error("Không sinh được giao dịch định kỳ", e);
            return List.of();
        }
    }
}
