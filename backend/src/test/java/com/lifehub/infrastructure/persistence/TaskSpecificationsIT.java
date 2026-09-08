package com.lifehub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifehub.domain.common.Page;
import com.lifehub.domain.common.PageRequest;
import com.lifehub.domain.task.Priority;
import com.lifehub.domain.task.Project;
import com.lifehub.domain.task.ProjectRepository;
import com.lifehub.domain.task.Tag;
import com.lifehub.domain.task.TagRepository;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskFilter;
import com.lifehub.domain.task.TaskRepository;
import com.lifehub.domain.task.TaskStatus;
import com.lifehub.support.TestDatabase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * T1-07 — dynamic filtering (FR-TSK-08, FR-TSK-09, FR-TSK-10).
 *
 * <p>Exercised against a real SQLite database rather than by inspecting the generated criteria
 * tree. What matters is that the query returns the right rows, and only running it can show that -
 * particularly for the timestamp range comparisons, where how Hibernate stores an Instant in SQLite
 * decides whether a range filter works at all.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TaskSpecificationsIT {

    private static final String DATABASE_URL = TestDatabase.freshUrl();
    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

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

    private Project lifehub;
    private Project other;
    private Tag docs;
    private Tag urgent;

    @BeforeEach
    void seed() {
        lifehub = projectRepository.save(new Project("LifeHub", null, null, null));
        other = projectRepository.save(new Project("Khác", null, null, null));
        docs = tagRepository.save(new Tag("docs", null));
        urgent = tagRepository.save(new Tag("gap", null));

        save("Viết SRS", lifehub, Priority.HIGH, TaskStatus.TODO, NOW.plus(1, ChronoUnit.DAYS), Set.of(docs));
        save("Viết ERD", lifehub, Priority.MEDIUM, TaskStatus.IN_PROGRESS, NOW.plus(3, ChronoUnit.DAYS), Set.of(docs, urgent));
        save("Họp team", lifehub, Priority.LOW, TaskStatus.DONE, NOW.plus(10, ChronoUnit.DAYS), Set.of());
        save("Mua cà phê", other, Priority.URGENT, TaskStatus.TODO, null, Set.of(urgent));
    }

    @Test
    @DisplayName("T1-07 — không lọc gì thì trả toàn bộ task còn sống")
    void returnsEverythingWhenUnfiltered() {
        assertThat(titles(TaskFilter.none()))
                .containsExactlyInAnyOrder("Viết SRS", "Viết ERD", "Họp team", "Mua cà phê");
    }

    @Test
    @DisplayName("T1-07 — lọc theo project")
    void filtersByProject() {
        TaskFilter filter = new TaskFilter(
                lifehub.getId(), null, null, null, null, null, null, false, false);

        assertThat(titles(filter)).containsExactlyInAnyOrder("Viết SRS", "Viết ERD", "Họp team");
    }

    @Test
    @DisplayName("T1-07 — lọc theo nhiều trạng thái cùng lúc")
    void filtersBySeveralStatuses() {
        TaskFilter filter = new TaskFilter(
                null, null, List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS), null, null, null, null, false, false);

        assertThat(titles(filter)).containsExactlyInAnyOrder("Viết SRS", "Viết ERD", "Mua cà phê");
    }

    @Test
    @DisplayName("T1-07 — lọc theo độ ưu tiên")
    void filtersByPriority() {
        TaskFilter filter = new TaskFilter(
                null, null, null, List.of(Priority.HIGH, Priority.URGENT), null, null, null, false, false);

        assertThat(titles(filter)).containsExactlyInAnyOrder("Viết SRS", "Mua cà phê");
    }

    @Test
    @DisplayName("T1-07 — lọc theo khoảng ngày đến hạn, biên là inclusive")
    void filtersByDueDateRange() {
        TaskFilter filter = new TaskFilter(
                null, null, null, null,
                NOW.plus(1, ChronoUnit.DAYS),
                NOW.plus(3, ChronoUnit.DAYS),
                null, false, false);

        assertThat(titles(filter))
                .as("task không có due_at phải bị loại khỏi bộ lọc theo khoảng ngày")
                .containsExactlyInAnyOrder("Viết SRS", "Viết ERD");
    }

    @Test
    @DisplayName("T1-07 — lọc theo nhãn, task có nhiều nhãn khớp chỉ xuất hiện một lần")
    void filtersByTagWithoutDuplicating() {
        TaskFilter filter = new TaskFilter(
                null, List.of(docs.getId(), urgent.getId()), null, null, null, null, null, false, false);

        assertThat(titles(filter))
                .containsExactlyInAnyOrder("Viết SRS", "Viết ERD", "Mua cà phê")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("T1-07 — tìm kiếm từ khóa quét cả tiêu đề và mô tả, không phân biệt hoa thường")
    void searchesTitleAndDescription() {
        assertThat(titles(keyword("viết"))).containsExactlyInAnyOrder("Viết SRS", "Viết ERD");
        assertThat(titles(keyword("VIẾT"))).hasSize(2);
        assertThat(titles(keyword("mô tả của Viết SRS"))).isEmpty();
        assertThat(titles(keyword("chi tiết"))).containsExactly("Viết SRS");
    }

    @Test
    @DisplayName("T1-07 — nhiều điều kiện kết hợp thu hẹp kết quả bằng AND")
    void combinesEveryCriterionWithAnd() {
        TaskFilter filter = new TaskFilter(
                lifehub.getId(),
                List.of(docs.getId()),
                List.of(TaskStatus.TODO),
                List.of(Priority.HIGH),
                NOW,
                NOW.plus(5, ChronoUnit.DAYS),
                "srs",
                false,
                false);

        assertThat(titles(filter)).containsExactly("Viết SRS");
    }

    @Test
    @DisplayName("T1-07 — task đã xóa mềm bị ẩn mặc định, hiện ra khi includeDeleted")
    void hidesSoftDeletedTasksUnlessAsked() {
        Task task = taskRepository.search(keyword("cà phê"), PageRequest.of(0, 10)).items().get(0);
        task.softDelete(NOW);
        taskRepository.save(task);

        assertThat(titles(keyword("cà phê"))).isEmpty();

        TaskFilter includingDeleted =
                new TaskFilter(null, null, null, null, null, null, "cà phê", true, false);
        assertThat(titles(includingDeleted)).containsExactly("Mua cà phê");
    }

    @Test
    @DisplayName("topLevelOnly loại subtask khỏi danh sách phẳng")
    void excludesSubtasksWhenAskedForTopLevelOnly() {
        Task parent = taskRepository.search(keyword("srs"), PageRequest.of(0, 10)).items().get(0);
        Task child = new Task("Rà soát SRS");
        child.attachTo(parent);
        taskRepository.save(child);

        TaskFilter topLevel = new TaskFilter(null, null, null, null, null, null, "srs", false, true);

        assertThat(titles(topLevel)).containsExactly("Viết SRS");
        assertThat(titles(keyword("srs"))).contains("Rà soát SRS");
    }

    @Test
    @DisplayName("FR-TSK-09 — sắp xếp theo ngày đến hạn và theo độ ưu tiên")
    void sortsByTheRequestedField() {
        Page<Task> byDueDate = taskRepository.search(
                new TaskFilter(lifehub.getId(), null, null, null, null, null, null, false, false),
                new PageRequest(0, 50, "dueAt", true));

        assertThat(byDueDate.items()).extracting(Task::getTitle)
                .containsExactly("Viết SRS", "Viết ERD", "Họp team");

        Page<Task> byPriorityDesc = taskRepository.search(
                TaskFilter.none(), new PageRequest(0, 50, "priority", false));

        assertThat(byPriorityDesc.items().get(0).getPriority()).isEqualTo(Priority.URGENT);
    }

    @Test
    @DisplayName("Phân trang trả đúng tổng số và số trang")
    void reportsPagingTotals() {
        Page<Task> firstPage = taskRepository.search(TaskFilter.none(), new PageRequest(0, 3, "title", true));

        assertThat(firstPage.items()).hasSize(3);
        assertThat(firstPage.totalItems()).isEqualTo(4);
        assertThat(firstPage.totalPages()).isEqualTo(2);
    }

    private TaskFilter keyword(String keyword) {
        return new TaskFilter(null, null, null, null, null, null, keyword, false, false);
    }

    private List<String> titles(TaskFilter filter) {
        return taskRepository.search(filter, PageRequest.of(0, 100)).items().stream()
                .map(Task::getTitle)
                .toList();
    }

    private void save(String title, Project project, Priority priority, TaskStatus status, Instant dueAt, Set<Tag> tags) {
        Task task = new Task(title);
        task.moveTo(project);
        task.prioritise(priority);
        task.changeStatus(status, NOW);
        task.schedule(dueAt);
        task.replaceTags(tags);
        if (title.equals("Viết SRS")) {
            task.describe("Mô tả chi tiết yêu cầu");
        }
        taskRepository.save(task);
    }
}
