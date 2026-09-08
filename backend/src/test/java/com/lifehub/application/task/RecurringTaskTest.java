package com.lifehub.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lifehub.application.calendar.RecurrenceExpander;
import com.lifehub.application.task.TaskCommands.CreateTask;
import com.lifehub.domain.task.Priority;
import com.lifehub.domain.task.Project;
import com.lifehub.domain.task.ProjectRepository;
import com.lifehub.domain.task.Tag;
import com.lifehub.domain.task.TagRepository;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskRepository;
import com.lifehub.domain.task.TaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Repeating tasks (FR-TSK-13).
 *
 * <p>The contract is that completing an instance produces the next one and leaves the completed
 * task alone as the record of that run - so the list never accumulates future instances the user
 * has not reached, and history is not overwritten.
 */
class RecurringTaskTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = ZonedDateTime.of(2026, 9, 15, 8, 0, 0, 0, VN).toInstant();
    private static final Instant DUE = ZonedDateTime.of(2026, 9, 15, 17, 0, 0, 0, VN).toInstant();

    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final TagRepository tagRepository = mock(TagRepository.class);

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(
                taskRepository,
                projectRepository,
                tagRepository,
                new RecurrenceExpander(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                VN);
        when(taskRepository.save(any(Task.class))).thenAnswer(call -> call.getArgument(0));
        when(taskRepository.findSubtasks(anyString())).thenReturn(List.of());
    }

    private Task repeatingTask(String rrule) {
        Task task = new Task("Nộp báo cáo tuần");
        task.schedule(DUE);
        task.repeat(rrule);
        return task;
    }

    @Test
    @DisplayName("Hoàn thành task lặp sinh instance kế tiếp với hạn chót đúng chu kỳ")
    void completingARepeatingTaskCreatesTheNextInstance() {
        Task task = repeatingTask("FREQ=WEEKLY");
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

        service.changeStatus("t1", TaskStatus.DONE);
        Optional<Task> next = service.spawnNextInstance(task, TaskStatus.DONE);

        assertThat(next).isPresent();
        assertThat(next.get().getDueAt()).isEqualTo(DUE.plus(Duration.ofDays(7)));
        assertThat(next.get().getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(next.get().getCompletedAt()).isNull();
        assertThat(next.get().getId()).isNotEqualTo(task.getId());
    }

    @Test
    @DisplayName("Instance kế tiếp giữ nguyên dự án, nhãn, độ ưu tiên và ước lượng")
    void theSuccessorInheritsEverythingTheUserConfigured() {
        Project project = new Project("LifeHub", "#6366f1", null, null);
        Tag tag = new Tag("báo cáo", "#10b981");
        Task task = repeatingTask("FREQ=MONTHLY");
        task.prioritise(Priority.HIGH);
        task.estimate(45);
        task.moveTo(project);
        task.addTag(tag);
        task.describe("Gửi cho quản lý trước 18h");

        Task next = service.spawnNextInstance(task, TaskStatus.DONE).orElseThrow();

        assertThat(next.getTitle()).isEqualTo("Nộp báo cáo tuần");
        assertThat(next.getDescription()).isEqualTo("Gửi cho quản lý trước 18h");
        assertThat(next.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(next.getEstimateMinutes()).isEqualTo(45);
        assertThat(next.getProject()).isEqualTo(project);
        assertThat(next.getTags()).containsExactly(tag);
        assertThat(next.getRrule()).isEqualTo("FREQ=MONTHLY");
    }

    @Test
    @DisplayName("Task đã hoàn thành không bị thay đổi, nó là bản ghi của lần chạy đó")
    void theCompletedInstanceIsLeftAsTheRecordOfThatRun() {
        Task task = repeatingTask("FREQ=DAILY");
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

        service.changeStatus("t1", TaskStatus.DONE);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(task.getCompletedAt()).isEqualTo(NOW);
        assertThat(task.getDueAt()).as("hạn chót cũ không bị dời sang chu kỳ mới").isEqualTo(DUE);
    }

    @Test
    @DisplayName("COUNT được tiêu thụ dần, lần cuối cùng không sinh thêm instance")
    void aCountedSeriesStopsWhenItIsSpent() {
        Task first = repeatingTask("FREQ=DAILY;COUNT=3");

        Task second = service.spawnNextInstance(first, TaskStatus.DONE).orElseThrow();
        assertThat(second.getRrule()).isEqualTo("FREQ=DAILY;COUNT=2");

        Task third = service.spawnNextInstance(second, TaskStatus.DONE).orElseThrow();
        assertThat(third.getRrule()).isEqualTo("FREQ=DAILY;COUNT=1");

        assertThat(service.spawnNextInstance(third, TaskStatus.DONE))
                .as("lặp 3 lần nghĩa là đúng 3 task, không phải một chuỗi vô tận")
                .isEmpty();
    }

    @Test
    @DisplayName("Chuỗi đã hết hạn theo UNTIL không sinh thêm instance")
    void anExpiredSeriesProducesNothing() {
        Task task = repeatingTask("FREQ=DAILY;UNTIL=20260916T000000Z");

        assertThat(service.spawnNextInstance(task, TaskStatus.DONE)).isEmpty();
    }

    @Test
    @DisplayName("Chỉ trạng thái DONE mới sinh instance kế tiếp")
    void onlyCompletionTriggersTheNextInstance() {
        Task task = repeatingTask("FREQ=WEEKLY");

        assertThat(service.spawnNextInstance(task, TaskStatus.IN_PROGRESS)).isEmpty();
        assertThat(service.spawnNextInstance(task, TaskStatus.CANCELLED)).isEmpty();
        assertThat(service.spawnNextInstance(task, null)).isEmpty();
        assertThat(service.spawnNextInstance(task, TaskStatus.DONE)).isPresent();
    }

    @Test
    @DisplayName("Task không có RRULE hoặc không có hạn chót thì không lặp")
    void aTaskWithoutARuleOrADeadlineDoesNotRepeat() {
        Task noRule = new Task("Việc lẻ");
        noRule.schedule(DUE);
        assertThat(service.spawnNextInstance(noRule, TaskStatus.DONE)).isEmpty();

        Task noDeadline = new Task("Việc lặp thiếu hạn");
        noDeadline.repeat("FREQ=WEEKLY");
        assertThat(noDeadline.isRepeating())
                .as("quy luật nói lặp bao lâu một lần, chỉ hạn chót mới nói lặp từ khi nào")
                .isFalse();
        assertThat(service.spawnNextInstance(noDeadline, TaskStatus.DONE)).isEmpty();
    }

    @Test
    @DisplayName("Subtask không lặp, vì instance kế tiếp sẽ không có chỗ đứng trong cây một cấp")
    void subtasksDoNotRepeat() {
        Task parent = new Task("Chuẩn bị báo cáo quý");
        Task subtask = repeatingTask("FREQ=WEEKLY");
        subtask.attachTo(parent);

        assertThat(subtask.isRepeating()).isFalse();
        assertThat(service.spawnNextInstance(subtask, TaskStatus.DONE)).isEmpty();
    }

    @Test
    @DisplayName("RRULE sai cú pháp bị từ chối ngay khi tạo task")
    void anInvalidRuleIsRejectedOnCreate() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(new CreateTask(
                        "Việc lặp", null, null, DUE, null, null, null, null, "KHONG-PHAI-RRULE")))
                .isInstanceOf(com.lifehub.domain.common.ValidationException.class);
    }
}
