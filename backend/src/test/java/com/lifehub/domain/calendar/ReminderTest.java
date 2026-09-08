package com.lifehub.domain.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.task.Task;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Reminder arithmetic and lifecycle (T2-09, T2-11). */
class ReminderTest {

    private static final Instant OCCURRENCE = Instant.parse("2026-09-15T07:00:00Z");

    private Event anEvent() {
        return new Event("Họp review sprint", OCCURRENCE, OCCURRENCE.plus(Duration.ofHours(1)));
    }

    @Nested
    @DisplayName("Tính thời điểm bắn")
    class TriggerTime {

        @Test
        @DisplayName("T2-09 — trigger_at bằng mốc sự kiện trừ đi offset_minutes")
        void triggerIsTheOccurrenceMinusTheOffset() {
            assertThat(Reminder.forEvent(anEvent(), OCCURRENCE, 15).getTriggerAt())
                    .isEqualTo(OCCURRENCE.minus(Duration.ofMinutes(15)));

            assertThat(Reminder.forEvent(anEvent(), OCCURRENCE, 1440).getTriggerAt())
                    .as("1440 phút là đúng một ngày trước")
                    .isEqualTo(OCCURRENCE.minus(Duration.ofDays(1)));
        }

        @Test
        @DisplayName("Offset 0 bắn đúng lúc sự kiện bắt đầu")
        void zeroOffsetFiresAtTheEventItself() {
            assertThat(Reminder.forEvent(anEvent(), OCCURRENCE, 0).getTriggerAt()).isEqualTo(OCCURRENCE);
        }

        @Test
        @DisplayName("Mốc sự kiện suy ngược lại được từ trigger_at và offset")
        void theOccurrenceIsRecoverableFromTheTrigger() {
            Reminder reminder = Reminder.forEvent(anEvent(), OCCURRENCE, 30);

            assertThat(reminder.occurrenceStart())
                    .as("cột occurrence không tồn tại trong schema; giá trị này phải suy ra được")
                    .isEqualTo(OCCURRENCE);
        }

        @Test
        @DisplayName("Chỉ nhận sáu khoảng nhắc mà FR-CAL-06 cho phép")
        void onlyTheSixAllowedOffsetsAreAccepted() {
            for (int offset : Reminder.ALLOWED_OFFSETS) {
                assertThat(Reminder.forEvent(anEvent(), OCCURRENCE, offset).isConfigured()).isTrue();
            }

            assertThatThrownBy(() -> Reminder.forEvent(anEvent(), OCCURRENCE, 7))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e ->
                            assertThat(((ValidationException) e).getField()).isEqualTo("offsetMinutes"));
        }
    }

    @Nested
    @DisplayName("Vòng đời")
    class Lifecycle {

        @Test
        @DisplayName("T2-11 — hoãn đánh dấu bản cũ SNOOZED và tạo bản mới PENDING đúng thời điểm")
        void snoozeMarksTheOldOneAndSchedulesAReplacement() {
            Reminder original = Reminder.forEvent(anEvent(), OCCURRENCE, 15);
            Instant newTrigger = OCCURRENCE.minus(Duration.ofMinutes(5));

            Reminder replacement = original.snoozeUntil(newTrigger);

            assertThat(original.getStatus()).isEqualTo(ReminderStatus.SNOOZED);
            assertThat(replacement.getStatus()).isEqualTo(ReminderStatus.PENDING);
            assertThat(replacement.getTriggerAt()).isEqualTo(newTrigger);
        }

        @Test
        @DisplayName("Bản hoãn vẫn trỏ về đúng mốc sự kiện gốc, không trôi theo lần hoãn")
        void theReplacementStillPointsAtTheOriginalOccurrence() {
            Reminder original = Reminder.forEvent(anEvent(), OCCURRENCE, 1440);

            Reminder replacement = original.snoozeUntil(OCCURRENCE.minus(Duration.ofMinutes(1430)));

            assertThat(replacement.occurrenceStart())
                    .as("thông báo phải nêu giờ họp thật, không phải giờ họp cộng thêm 10 phút")
                    .isEqualTo(OCCURRENCE);
            assertThat(replacement.getOffsetMinutes()).isEqualTo(1430);
        }

        @Test
        @DisplayName("Bản hoãn mang offset lẻ nên không bị coi là cấu hình của event")
        void theReplacementIsNotMistakenForConfiguration() {
            Reminder replacement = Reminder.forEvent(anEvent(), OCCURRENCE, 15)
                    .snoozeUntil(OCCURRENCE.minus(Duration.ofMinutes(3)));

            assertThat(replacement.isConfigured())
                    .as("nếu không, form sửa event sẽ hiện thêm một mục nhắc mà user chưa từng chọn")
                    .isFalse();
        }

        @Test
        @DisplayName("Hoãn sau khi sự kiện đã bắt đầu cho offset âm, vẫn nhất quán")
        void snoozingAfterTheEventStartedGivesANegativeOffset() {
            Reminder replacement = Reminder.forEvent(anEvent(), OCCURRENCE, 0)
                    .snoozeUntil(OCCURRENCE.plus(Duration.ofMinutes(10)));

            assertThat(replacement.getOffsetMinutes()).isEqualTo(-10);
            assertThat(replacement.occurrenceStart()).isEqualTo(OCCURRENCE);
        }

        @Test
        @DisplayName("Chỉ reminder PENDING quá hạn dưới 24 giờ mới tính là bị lỡ")
        void onlyRecentPendingRemindersCountAsMissed() {
            Reminder reminder = Reminder.forEvent(anEvent(), OCCURRENCE, 0);

            assertThat(reminder.isMissedAt(OCCURRENCE.minusSeconds(1)))
                    .as("chưa tới hạn")
                    .isFalse();
            assertThat(reminder.isMissedAt(OCCURRENCE.plus(Duration.ofHours(3)))).isTrue();
            assertThat(reminder.isMissedAt(OCCURRENCE.plus(Duration.ofHours(25))))
                    .as("quá 24 giờ thì thuộc về EXPIRED, không hiện trong modal khởi động")
                    .isFalse();

            reminder.dismiss();
            assertThat(reminder.isMissedAt(OCCURRENCE.plus(Duration.ofHours(3)))).isFalse();
        }

        @Test
        @DisplayName("Trạng thái kết thúc thì không hành động được nữa")
        void closedStatusesAreFinal() {
            assertThat(ReminderStatus.DISMISSED.isClosed()).isTrue();
            assertThat(ReminderStatus.EXPIRED.isClosed()).isTrue();
            assertThat(ReminderStatus.FIRED.isClosed()).isFalse();
            assertThat(ReminderStatus.PENDING.isPending()).isTrue();
        }

        @Test
        @DisplayName("Reminder gắn task dùng hạn chót làm mốc")
        void aTaskReminderAnchorsOnTheDeadline() {
            Task task = new Task("Nộp báo cáo");

            Reminder reminder = Reminder.forTask(task, OCCURRENCE, 60);

            assertThat(reminder.getTask()).isEqualTo(task);
            assertThat(reminder.getEvent()).isNull();
            assertThat(reminder.occurrenceStart()).isEqualTo(OCCURRENCE);
        }
    }
}
