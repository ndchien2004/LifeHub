package com.lifehub.infrastructure.scheduler;

import com.lifehub.domain.task.TaskRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes the 30 day undo window on deleted tasks (FR-TSK-03).
 *
 * <p>FR-TSK-03 promises recovery "within 30 days", which only means anything if something
 * eventually removes the rows - otherwise soft deleted tasks accumulate forever. Nothing in the
 * documentation owned this cleanup, so it runs here at startup, mirroring the retention policy
 * already specified for {@code ai_parse_log} (03-DATA-MODEL.md §2.10).
 */
@Component
public class DeletedTaskPurger {

    private static final Logger log = LoggerFactory.getLogger(DeletedTaskPurger.class);
    static final Duration RETENTION = Duration.ofDays(30);

    private final TaskRepository taskRepository;
    private final Clock clock;

    public DeletedTaskPurger(TaskRepository taskRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void purgeOnStartup() {
        purge();
    }

    /** Returns how many rows were removed, so the behaviour is assertable in a test. */
    @Transactional
    public int purge() {
        Instant cutoff = clock.instant().minus(RETENTION);
        int removed = taskRepository.purgeDeletedBefore(cutoff);
        if (removed > 0) {
            log.info("Đã xóa vĩnh viễn {} task nằm trong thùng rác quá {} ngày", removed, RETENTION.toDays());
        }
        return removed;
    }
}
