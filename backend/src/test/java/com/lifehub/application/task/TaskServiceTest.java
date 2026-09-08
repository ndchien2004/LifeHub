package com.lifehub.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifehub.application.calendar.RecurrenceExpander;
import com.lifehub.application.task.TaskCommands.CreateTask;
import com.lifehub.domain.common.Patch;
import com.lifehub.application.task.TaskCommands.ReorderEntry;
import com.lifehub.application.task.TaskCommands.UpdateTask;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.task.Priority;
import com.lifehub.domain.task.Project;
import com.lifehub.domain.task.ProjectRepository;
import com.lifehub.domain.task.Tag;
import com.lifehub.domain.task.TagRepository;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskRepository;
import com.lifehub.domain.task.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Write side behaviour.
 *
 * <p>The clock is fixed, so a completion timestamp can be asserted exactly rather than within a
 * tolerance window (08-TEST-PLAN.md section 2).
 */
class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final TagRepository tagRepository = mock(TagRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(
                taskRepository, projectRepository, tagRepository, new RecurrenceExpander(), clock, ZoneOffset.UTC);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.findSubtasks(anyString())).thenReturn(List.of());
        when(taskRepository.findSubtasksIncludingDeleted(anyString())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Tạo task với đầy đủ thuộc tính")
    void createsATaskWithEveryAttribute() {
        Project project = new Project("LifeHub", "#6366f1", null, null);
        Tag tag = new Tag("docs", "#10b981");
        when(projectRepository.findById("p1")).thenReturn(Optional.of(project));
        when(tagRepository.findAllById(Set.of("t1"))).thenReturn(Set.of(tag));

        Task task = service.create(new CreateTask(
                "Viết SRS",
                "Đủ use case và ERD",
                Priority.HIGH,
                NOW.plusSeconds(3600),
                "p1",
                null,
                List.of("t1"),
                180,
                null));

        assertThat(task.getTitle()).isEqualTo("Viết SRS");
        assertThat(task.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(task.getProject()).isEqualTo(project);
        assertThat(task.getTags()).containsExactly(tag);
        assertThat(task.getEstimateMinutes()).isEqualTo(180);
    }

    @Test
    @DisplayName("T1-01 — tạo task tiêu đề rỗng thì không có gì được lưu")
    void doesNotSaveWhenTheTitleIsEmpty() {
        assertThatThrownBy(() -> service.create(
                        new CreateTask("", null, null, null, null, null, null, null, null)))
                .isInstanceOf(ValidationException.class);

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("T1-04 — tạo subtask của subtask bị chặn ở tầng service")
    void refusesToNestBeyondOneLevel() {
        Task parent = new Task("Chuẩn bị release");
        Task child = new Task("Viết changelog");
        child.attachTo(parent);
        when(taskRepository.findById("child")).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> service.create(
                        new CreateTask("Rà soát", null, null, null, null, "child", null, null, null)))
                .isInstanceOf(Task.SubtaskDepthException.class);

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("T1-02 — đổi trạng thái sang DONE ghi completed_at theo clock đã inject")
    void stampsCompletionFromTheInjectedClock() {
        Task task = new Task("Viết SRS");
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

        Task result = service.changeStatus("t1", TaskStatus.DONE);

        assertThat(result.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Đổi trạng thái task không tồn tại trả NotFound")
    void reportsAMissingTask() {
        when(taskRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus("missing", TaskStatus.DONE))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("PATCH chỉ đụng vào các trường được gửi lên")
    void appliesOnlyThePresentFields() {
        Task task = new Task("Viết SRS");
        task.prioritise(Priority.HIGH);
        task.schedule(NOW.plusSeconds(3600));
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

        service.update("t1", new UpdateTask(
                Patch.of("Viết SRS v2"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()));

        assertThat(task.getTitle()).isEqualTo("Viết SRS v2");
        assertThat(task.getPriority()).as("không gửi lên thì giữ nguyên").isEqualTo(Priority.HIGH);
        assertThat(task.getDueAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    @DisplayName("PATCH gửi null tường minh thì xóa trắng trường đó")
    void clearsAFieldExplicitlySetToNull() {
        Task task = new Task("Viết SRS");
        task.schedule(NOW.plusSeconds(3600));
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

        service.update("t1", new UpdateTask(
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.of(null),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()));

        assertThat(task.getDueAt()).as("gửi dueAt: null nghĩa là bỏ hạn chót").isNull();
    }

    @Test
    @DisplayName("Gán nhãn không tồn tại bị từ chối thay vì lưu một phần")
    void rejectsUnknownTagIds() {
        when(tagRepository.findAllById(Set.of("ghost"))).thenReturn(Set.of());

        assertThatThrownBy(() -> service.create(
                        new CreateTask("Viết SRS", null, null, null, null, null, List.of("ghost"), null, null)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getField()).isEqualTo("tagIds"));
    }

    @Test
    @DisplayName("Xóa task cha thì subtask bị xóa mềm theo, không bị bỏ lại mồ côi")
    void softDeletesSubtasksAlongsideTheirParent() {
        Task parent = new Task("Chuẩn bị release");
        Task child = new Task("Viết changelog");
        child.attachTo(parent);
        when(taskRepository.findById("p1")).thenReturn(Optional.of(parent));
        when(taskRepository.findSubtasks("p1")).thenReturn(List.of(child));

        service.delete("p1");

        assertThat(parent.isDeleted()).isTrue();
        assertThat(parent.getDeletedAt()).isEqualTo(NOW);
        assertThat(child.isDeleted()).as("subtask phải biến mất cùng cha").isTrue();
        verify(taskRepository, times(2)).save(any(Task.class));
    }

    @Test
    @DisplayName("Khôi phục task cha cũng khôi phục subtask của nó")
    void restoresSubtasksAlongsideTheirParent() {
        Task parent = new Task("Chuẩn bị release");
        Task child = new Task("Viết changelog");
        parent.softDelete(NOW);
        child.softDelete(NOW);
        when(taskRepository.findByIdIncludingDeleted("p1")).thenReturn(Optional.of(parent));
        // Restore has to look at deleted children: the live subtask query cannot see them.
        when(taskRepository.findSubtasksIncludingDeleted("p1")).thenReturn(List.of(child));

        service.restore("p1");

        assertThat(parent.isDeleted()).isFalse();
        assertThat(child.isDeleted()).as("subtask phải sống lại cùng cha").isFalse();
    }

    @Test
    @DisplayName("Khôi phục dùng truy vấn subtask bao gồm bản ghi đã xóa, không phải truy vấn thường")
    void looksUpDeletedSubtasksWhenRestoring() {
        Task parent = new Task("Chuẩn bị release");
        parent.softDelete(NOW);
        when(taskRepository.findByIdIncludingDeleted("p1")).thenReturn(Optional.of(parent));

        service.restore("p1");

        verify(taskRepository).findSubtasksIncludingDeleted("p1");
        verify(taskRepository, never()).findSubtasks("p1");
    }

    @Test
    @DisplayName("Khôi phục task chưa từng tồn tại trả NotFound")
    void reportsAMissingTaskOnRestore() {
        when(taskRepository.findByIdIncludingDeleted("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore("ghost")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Reorder cập nhật đúng sort_order cho từng task được gửi lên")
    void appliesEveryReorderEntry() {
        Task first = new Task("A");
        Task second = new Task("B");
        when(taskRepository.findAllById(List.of(first.getId(), second.getId())))
                .thenReturn(List.of(first, second));

        service.reorder(List.of(new ReorderEntry(first.getId(), 5), new ReorderEntry(second.getId(), 2)));

        assertThat(first.getSortOrder()).isEqualTo(5);
        assertThat(second.getSortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("Reorder danh sách rỗng không chạm vào database")
    void skipsAnEmptyReorder() {
        service.reorder(List.of());
        service.reorder(null);

        verify(taskRepository, never()).findAllById(any());
    }
}
