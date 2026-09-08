package com.lifehub.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifehub.domain.common.PageRequest;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskFilter;
import com.lifehub.domain.task.TaskRepository;
import com.lifehub.support.TestDatabase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The 30 day recovery window promised by FR-TSK-03.
 *
 * <p>Uses its own clock rather than the application bean, so "31 days later" can be expressed
 * without waiting for it.
 */
@SpringBootTest
@ActiveProfiles("test")
class DeletedTaskPurgerIT {

    private static final String DATABASE_URL = TestDatabase.freshUrl();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("FR-TSK-03 — task xóa quá 30 ngày bị xóa vĩnh viễn, trong 30 ngày thì giữ lại")
    void purgesOnlyTasksPastTheRecoveryWindow() {
        Instant now = Instant.now();

        String recent = softDeleted("Xóa hôm qua", now.minus(1, ChronoUnit.DAYS));
        String edge = softDeleted("Xóa đúng 29 ngày trước", now.minus(29, ChronoUnit.DAYS));
        String expired = softDeleted("Xóa 31 ngày trước", now.minus(31, ChronoUnit.DAYS));
        Task live = taskRepository.save(new Task("Task còn sống"));

        assertThat(purgeAt(now)).isEqualTo(1);

        assertThat(allIds())
                .contains(recent, edge, live.getId())
                .doesNotContain(expired);
    }

    @Test
    @DisplayName("Không có gì quá hạn thì không xóa dòng nào")
    void purgesNothingWhenEverythingIsWithinTheWindow() {
        Instant now = Instant.now();
        taskRepository.save(new Task("Task bình thường"));

        assertThat(purgeAt(now)).isZero();
    }

    /**
     * Runs the purge at a chosen point in time.
     *
     * <p>Constructed by hand so the clock can be fixed, which means Spring is not proxying it and
     * its {@code @Transactional} annotation has no effect - a bulk delete needs a real transaction,
     * so the template supplies one explicitly.
     */
    private int purgeAt(Instant now) {
        DeletedTaskPurger purger = new DeletedTaskPurger(taskRepository, Clock.fixed(now, ZoneOffset.UTC));
        return transactionTemplate.execute(status -> purger.purge());
    }

    private String softDeleted(String title, Instant deletedAt) {
        Task task = new Task(title);
        task.softDelete(deletedAt);
        return taskRepository.save(task).getId();
    }

    private List<String> allIds() {
        TaskFilter includingDeleted =
                new TaskFilter(null, null, null, null, null, null, null, true, false);
        return taskRepository.search(includingDeleted, PageRequest.of(0, 200)).items().stream()
                .map(Task::getId)
                .toList();
    }
}
