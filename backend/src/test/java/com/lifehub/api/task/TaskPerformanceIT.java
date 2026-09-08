package com.lifehub.api.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifehub.domain.task.Priority;
import com.lifehub.domain.task.Project;
import com.lifehub.domain.task.ProjectRepository;
import com.lifehub.domain.task.Tag;
import com.lifehub.domain.task.TagRepository;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskRepository;
import com.lifehub.domain.task.TaskStatus;
import com.lifehub.support.ApiIntegrationTest;
import com.lifehub.support.TestDatabase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T1-12 — a thousand tasks, first page under 500 ms.
 *
 * <p>The number that matters is not raw speed but the absence of an N+1: every row carries a
 * project, a tag set and a subtask count, and fetching those per row rather than per page is the
 * failure this test exists to catch.
 */
class TaskPerformanceIT extends ApiIntegrationTest {

    private static final String DATABASE_URL = TestDatabase.freshUrl();
    private static final int TASK_COUNT = 1_000;
    private static final long BUDGET_MS = 500;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TagRepository tagRepository;

    private static boolean seeded;

    @BeforeAll
    static void resetSeedFlag() {
        seeded = false;
    }

    private void seedOnce() {
        if (seeded) {
            return;
        }
        Instant now = Instant.now();
        Random random = new Random(42);

        List<Project> projects = List.of(
                projectRepository.save(new Project("Công việc", null, null, null)),
                projectRepository.save(new Project("Cá nhân", null, null, null)),
                projectRepository.save(new Project("Học tập", null, null, null)));
        List<Tag> tags = List.of(
                tagRepository.save(new Tag("gap", null)),
                tagRepository.save(new Tag("docs", null)),
                tagRepository.save(new Tag("hop", null)));

        TaskStatus[] statuses = TaskStatus.values();
        Priority[] priorities = Priority.values();

        for (int i = 0; i < TASK_COUNT; i++) {
            Task task = new Task("Task số " + i);
            task.describe("Mô tả cho task số " + i);
            task.moveTo(projects.get(random.nextInt(projects.size())));
            task.prioritise(priorities[random.nextInt(priorities.length)]);
            task.changeStatus(statuses[random.nextInt(statuses.length)], now);
            task.schedule(now.plus(random.nextInt(60) - 30, ChronoUnit.DAYS));
            task.replaceTags(Set.of(tags.get(random.nextInt(tags.size()))));
            taskRepository.save(task);
        }
        seeded = true;
    }

    @Test
    @DisplayName("T1-12 — 1.000 task, load trang đầu 50 dòng trong ≤ 500 ms")
    void loadsTheFirstPageWithinBudget() throws Exception {
        seedOnce();

        // Warm up: the first call pays for Hibernate query plan compilation and JIT, which is a
        // one-off startup cost rather than the per-request cost NFR-PERF-02 talks about.
        mockMvc.perform(authed(get("/api/v1/tasks").param("size", "50"))).andExpect(status().isOk());

        long start = System.nanoTime();
        mockMvc.perform(authed(get("/api/v1/tasks").param("size", "50").param("sort", "dueAt,asc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(50))
                .andExpect(jsonPath("$.data.totalItems").value(TASK_COUNT));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs)
                .as("T1-12 / NFR-PERF-02: trang đầu của 1.000 task phải trả trong %d ms, thực tế %d ms",
                        BUDGET_MS, elapsedMs)
                .isLessThanOrEqualTo(BUDGET_MS);
    }

    @Test
    @DisplayName("Lọc kết hợp trên 1.000 task vẫn nằm trong ngân sách thời gian")
    void filtersWithinBudget() throws Exception {
        seedOnce();

        mockMvc.perform(authed(get("/api/v1/tasks").param("status", "TODO"))).andExpect(status().isOk());

        long start = System.nanoTime();
        mockMvc.perform(authed(get("/api/v1/tasks")
                        .param("status", "TODO,IN_PROGRESS")
                        .param("priority", "HIGH,URGENT")
                        .param("q", "task")
                        .param("size", "50")))
                .andExpect(status().isOk());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThanOrEqualTo(BUDGET_MS);
    }
}
