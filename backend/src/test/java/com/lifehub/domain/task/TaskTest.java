package com.lifehub.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lifehub.domain.common.DomainException;
import com.lifehub.domain.common.ValidationException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Business rules that live on the Task entity itself. */
class TaskTest {

    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

    @Nested
    @DisplayName("Tiêu đề")
    class Title {

        @Test
        @DisplayName("T1-01 — tiêu đề rỗng bị từ chối")
        void rejectsAnEmptyTitle() {
            assertThatThrownBy(() -> new Task(""))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Tiêu đề không được để trống");
        }

        @Test
        @DisplayName("T1-01 — tiêu đề chỉ có khoảng trắng cũng bị từ chối")
        void rejectsAWhitespaceOnlyTitle() {
            assertThatThrownBy(() -> new Task("    ")).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> new Task(null)).isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("Tiêu đề được cắt khoảng trắng thừa hai đầu")
        void trimsSurroundingWhitespace() {
            assertThat(new Task("  Viết SRS  ").getTitle()).isEqualTo("Viết SRS");
        }

        @Test
        @DisplayName("Tiêu đề quá 255 ký tự bị từ chối, đúng 255 thì chấp nhận")
        void enforcesTheLengthLimit() {
            assertThatCode(() -> new Task("x".repeat(255))).doesNotThrowAnyException();
            assertThatThrownBy(() -> new Task("x".repeat(256)))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> assertThat(((ValidationException) e).getField()).isEqualTo("title"));
        }
    }

    @Nested
    @DisplayName("Chuyển trạng thái")
    class StatusTransitions {

        @Test
        @DisplayName("T1-02 — chuyển sang DONE thì ghi completed_at")
        void stampsCompletionWhenFinished() {
            Task task = new Task("Viết SRS");

            task.changeStatus(TaskStatus.DONE, NOW);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
            assertThat(task.getCompletedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("T1-03 — mở lại task đã DONE thì xóa completed_at về null")
        void clearsCompletionWhenReopened() {
            Task task = new Task("Viết SRS");
            task.changeStatus(TaskStatus.DONE, NOW);

            task.changeStatus(TaskStatus.TODO, NOW.plus(1, ChronoUnit.HOURS));

            assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
            assertThat(task.getCompletedAt())
                    .as("mốc hoàn thành cũ phải biến mất, không được giữ lại")
                    .isNull();
        }

        @Test
        @DisplayName("Chuyển sang CANCELLED cũng không giữ lại completed_at")
        void clearsCompletionWhenCancelled() {
            Task task = new Task("Viết SRS");
            task.changeStatus(TaskStatus.DONE, NOW);

            task.changeStatus(TaskStatus.CANCELLED, NOW);

            assertThat(task.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("Chuyển sang chính trạng thái hiện tại không đổi completed_at")
        void isANoOpWhenTheStatusDoesNotChange() {
            Task task = new Task("Viết SRS");
            task.changeStatus(TaskStatus.DONE, NOW);

            task.changeStatus(TaskStatus.DONE, NOW.plus(5, ChronoUnit.HOURS));

            assertThat(task.getCompletedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("Trạng thái null bị từ chối")
        void rejectsANullStatus() {
            assertThatThrownBy(() -> new Task("Viết SRS").changeStatus(null, NOW))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    @DisplayName("Subtask một cấp (FR-TSK-11)")
    class Subtasks {

        @Test
        @DisplayName("Task thường nhận được subtask")
        void allowsOneLevelOfNesting() {
            Task parent = new Task("Chuẩn bị release");
            Task child = new Task("Viết changelog");

            child.attachTo(parent);

            assertThat(child.isSubtask()).isTrue();
            assertThat(child.getParent()).isEqualTo(parent);
            assertThat(parent.isSubtask()).isFalse();
        }

        @Test
        @DisplayName("T1-04 — subtask của subtask bị từ chối")
        void rejectsNestingBeyondOneLevel() {
            Task parent = new Task("Chuẩn bị release");
            Task child = new Task("Viết changelog");
            child.attachTo(parent);
            Task grandchild = new Task("Rà soát chính tả");

            assertThatThrownBy(() -> grandchild.attachTo(child))
                    .isInstanceOf(DomainException.class)
                    .isInstanceOf(Task.SubtaskDepthException.class)
                    .hasMessageContaining("một cấp");
        }

        @Test
        @DisplayName("Task không thể làm subtask của chính nó")
        void rejectsSelfParenting() {
            Task task = new Task("Chuẩn bị release");

            assertThatThrownBy(() -> task.attachTo(task)).isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("Gỡ khỏi cha bằng cách gán parent null")
        void detachesWhenGivenNoParent() {
            Task parent = new Task("Chuẩn bị release");
            Task child = new Task("Viết changelog");
            child.attachTo(parent);

            child.attachTo(null);

            assertThat(child.isSubtask()).isFalse();
        }
    }

    @Nested
    @DisplayName("Quá hạn (FR-TSK-12)")
    class Overdue {

        @Test
        @DisplayName("T1-05 — due_at đã qua và chưa xong thì là quá hạn")
        void flagsAPastDeadline() {
            Task task = new Task("Nộp báo cáo");
            task.schedule(NOW.minus(1, ChronoUnit.HOURS));

            assertThat(task.isOverdue(NOW)).isTrue();
        }

        @Test
        @DisplayName("T1-05 — IN_PROGRESS quá hạn vẫn tính là quá hạn")
        void flagsAnInProgressTaskPastItsDeadline() {
            Task task = new Task("Nộp báo cáo");
            task.schedule(NOW.minus(1, ChronoUnit.HOURS));
            task.changeStatus(TaskStatus.IN_PROGRESS, NOW);

            assertThat(task.isOverdue(NOW)).isTrue();
        }

        @Test
        @DisplayName("T1-06 — task DONE dù quá hạn vẫn không bị đánh dấu")
        void doesNotFlagACompletedTask() {
            Task task = new Task("Nộp báo cáo");
            task.schedule(NOW.minus(10, ChronoUnit.DAYS));
            task.changeStatus(TaskStatus.DONE, NOW);

            assertThat(task.isOverdue(NOW)).isFalse();
        }

        @Test
        @DisplayName("Không có due_at thì không bao giờ quá hạn")
        void neverFlagsATaskWithoutADeadline() {
            assertThat(new Task("Đọc sách").isOverdue(NOW)).isFalse();
        }

        @Test
        @DisplayName("Deadline trong tương lai thì chưa quá hạn")
        void doesNotFlagAFutureDeadline() {
            Task task = new Task("Nộp báo cáo");
            task.schedule(NOW.plus(1, ChronoUnit.HOURS));

            assertThat(task.isOverdue(NOW)).isFalse();
        }

        @Test
        @DisplayName("Task CANCELLED quá hạn VẪN bị đánh dấu — đúng theo T1-05, xem ghi chú PROGRESS.md")
        void documentsTheCancelledBehaviour() {
            Task task = new Task("Nộp báo cáo");
            task.schedule(NOW.minus(1, ChronoUnit.HOURS));
            task.changeStatus(TaskStatus.CANCELLED, NOW);

            assertThat(task.isOverdue(NOW))
                    .as("T1-05 chỉ loại trừ DONE; cần user quyết định có loại trừ CANCELLED không")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Các thuộc tính khác")
    class OtherAttributes {

        @Test
        @DisplayName("Ước lượng thời gian phải lớn hơn 0, null là hợp lệ")
        void validatesTheEstimate() {
            Task task = new Task("Viết SRS");

            assertThatCode(() -> task.estimate(null)).doesNotThrowAnyException();
            assertThatCode(() -> task.estimate(30)).doesNotThrowAnyException();
            assertThatThrownBy(() -> task.estimate(0)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> task.estimate(-5)).isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("Soft delete rồi khôi phục trả task về trạng thái còn sống")
        void softDeletesAndRestores() {
            Task task = new Task("Viết SRS");

            task.softDelete(NOW);
            assertThat(task.isDeleted()).isTrue();
            assertThat(task.getDeletedAt()).isEqualTo(NOW);

            task.restore();
            assertThat(task.isDeleted()).isFalse();
            assertThat(task.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("Gán lại nhãn thay thế toàn bộ tập cũ")
        void replacesTheWholeTagSet() {
            Task task = new Task("Viết SRS");
            Tag docs = new Tag("docs", "#10b981");
            Tag urgent = new Tag("gấp", "#ef4444");
            task.addTag(docs);

            task.replaceTags(java.util.Set.of(urgent));

            assertThat(task.getTags()).containsExactly(urgent);
        }

        @Test
        @DisplayName("Ưu tiên mặc định MEDIUM, trạng thái mặc định TODO")
        void startsWithTheDocumentedDefaults() {
            Task task = new Task("Viết SRS");

            assertThat(task.getPriority()).isEqualTo(Priority.MEDIUM);
            assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
            assertThat(task.getSortOrder()).isZero();
        }
    }
}
