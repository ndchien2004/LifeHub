package com.lifehub.application.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifehub.domain.calendar.Event;
import com.lifehub.domain.calendar.EventException;
import com.lifehub.domain.calendar.EventExceptionRepository;
import com.lifehub.domain.calendar.Reminder;
import com.lifehub.domain.calendar.ReminderRepository;
import com.lifehub.domain.calendar.ReminderStatus;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskRepository;
import com.lifehub.domain.task.TaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Reminder generation and resolution (T2-10 to T2-12).
 *
 * <p>The clock is fixed throughout, which is the only way "the scheduler fires what is due and
 * nothing else" can be asserted rather than approximated (08-TEST-PLAN.md §2).
 */
class ReminderServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = ZonedDateTime.of(2026, 9, 15, 12, 0, 0, 0, VN).toInstant();

    private final ReminderRepository reminderRepository = mock(ReminderRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final EventExceptionRepository exceptionRepository = mock(EventExceptionRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private ReminderService service;

    @BeforeEach
    void setUp() {
        service = new ReminderService(
                reminderRepository,
                taskRepository,
                new OccurrenceResolver(new RecurrenceExpander(), exceptionRepository),
                clock,
                VN);

        when(reminderRepository.save(any(Reminder.class))).thenAnswer(call -> call.getArgument(0));
        when(reminderRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        when(reminderRepository.findByEventId(anyString())).thenReturn(List.of());
        when(exceptionRepository.findByEventId(anyString())).thenReturn(List.of());
    }

    private Event eventAt(Instant start, String rrule) {
        Event event = new Event("Họp review sprint", start, start.plus(Duration.ofHours(1)));
        event.relocate("Phòng họp A");
        event.inZone(VN.getId());
        event.repeat(rrule);
        return event;
    }

    @SuppressWarnings("unchecked")
    private List<Reminder> capturedSaveAll() {
        ArgumentCaptor<List<Reminder>> captor = ArgumentCaptor.forClass(List.class);
        verify(reminderRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Sinh reminder")
    class Generation {

        @Test
        @DisplayName("Event không lặp sinh một reminder cho mỗi khoảng nhắc đã chọn")
        void oneReminderPerConfiguredOffset() {
            Instant start = NOW.plus(Duration.ofHours(3));

            service.syncForEvent(eventAt(start, null), List.of(15, 60));

            assertThat(capturedSaveAll())
                    .extracting(Reminder::getTriggerAt)
                    .containsExactlyInAnyOrder(
                            start.minus(Duration.ofMinutes(15)), start.minus(Duration.ofHours(1)));
        }

        @Test
        @DisplayName("Event lặp sinh reminder cho mọi instance trong 90 ngày tới")
        void aRepeatingEventGetsOnePerOccurrenceInsideTheHorizon() {
            service.syncForEvent(eventAt(NOW.plus(Duration.ofHours(3)), "FREQ=WEEKLY"), List.of(15));

            assertThat(capturedSaveAll())
                    .as("13 tuần nằm trong chân trời 90 ngày")
                    .hasSize(13);
        }

        @Test
        @DisplayName("Instance bị hủy không sinh reminder")
        void aCancelledOccurrenceGetsNoReminder() {
            Instant start = NOW.plus(Duration.ofHours(3));
            Event event = eventAt(start, "FREQ=DAILY;COUNT=3");
            EventException cancelled = new EventException(event, start.plus(Duration.ofDays(1)));
            cancelled.cancel();
            when(exceptionRepository.findByEventId(event.getId())).thenReturn(List.of(cancelled));

            service.syncForEvent(event, List.of(0));

            assertThat(capturedSaveAll()).hasSize(2);
        }

        @Test
        @DisplayName("Danh sách khoảng nhắc rỗng nghĩa là gỡ hết reminder chưa bắn")
        void anEmptyOffsetListClearsThePendingReminders() {
            Instant start = NOW.plus(Duration.ofHours(3));
            Event event = eventAt(start, null);
            Reminder existing = Reminder.forEvent(event, start, 15);
            when(reminderRepository.findByEventId(event.getId())).thenReturn(List.of(existing));

            service.syncForEvent(event, List.of());

            verify(reminderRepository).deleteAll(List.of(existing));
        }

        @Test
        @DisplayName("null nghĩa là không đụng tới reminder hiện có")
        void nullOffsetsLeaveEverythingAlone() {
            service.syncForEvent(eventAt(NOW.plus(Duration.ofHours(3)), null), null);

            verify(reminderRepository, org.mockito.Mockito.never()).saveAll(anyList());
            verify(reminderRepository, org.mockito.Mockito.never()).deleteAll(anyList());
        }

        @Test
        @DisplayName("Reminder đã bắn không bị sinh lại, nên thông báo không hiện lần thứ hai")
        void alreadyFiredRemindersAreNotRecreated() {
            Instant start = NOW.plus(Duration.ofHours(3));
            Event event = eventAt(start, null);
            Reminder fired = Reminder.forEvent(event, start, 15);
            fired.markFired(NOW);
            when(reminderRepository.findByEventId(event.getId())).thenReturn(List.of(fired));

            service.syncForEvent(event, List.of(15));

            assertThat(capturedSaveAll()).isEmpty();
        }

        @Test
        @DisplayName("Khoảng nhắc ngoài danh sách cho phép bị từ chối")
        void rejectsAnOffsetOutsideTheAllowedSet() {
            Event event = eventAt(NOW.plus(Duration.ofHours(3)), null);

            assertThatThrownBy(() -> service.syncForEvent(event, List.of(7)))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> assertThat(((ValidationException) e).getField())
                            .isEqualTo("reminderOffsets"));
        }

        @Test
        @DisplayName("Khoảng nhắc cấu hình đọc lại được, bỏ qua bản hoãn có offset lẻ")
        void configuredOffsetsIgnoreSnoozeRows() {
            Instant start = NOW.plus(Duration.ofHours(3));
            Event event = eventAt(start, null);
            Reminder configured = Reminder.forEvent(event, start, 15);
            Reminder snoozed = Reminder.forEvent(event, start, 60)
                    .snoozeUntil(start.minus(Duration.ofMinutes(37)));
            when(reminderRepository.findByEventId(event.getId()))
                    .thenReturn(List.of(configured, snoozed));

            assertThat(service.configuredOffsets(event.getId())).containsExactly(15);
        }
    }

    @Nested
    @DisplayName("Scheduler và trạng thái")
    class Firing {

        @Test
        @DisplayName("T2-10 — chỉ reminder đã tới hạn được bắn, reminder tương lai thì không")
        void firesOnlyWhatIsDue() {
            Instant occurrence = NOW.plus(Duration.ofMinutes(15));
            Event event = eventAt(occurrence, null);
            Reminder due = Reminder.forEvent(event, occurrence, 15);
            when(reminderRepository.findDue(NOW)).thenReturn(List.of(due));

            List<ReminderNotification> fired = service.fireDue();

            assertThat(due.getStatus()).isEqualTo(ReminderStatus.FIRED);
            assertThat(due.getFiredAt()).isEqualTo(NOW);
            assertThat(fired).singleElement().satisfies(notification -> {
                assertThat(notification.title()).isEqualTo("Họp review sprint");
                assertThat(notification.body())
                        .as("06-API-SPEC.md §6: giờ theo múi giờ hiển thị, rồi tới địa điểm")
                        .isEqualTo("12:15 · Phòng họp A");
                assertThat(notification.refType()).isEqualTo("EVENT");
                assertThat(notification.refId()).isEqualTo(event.getId());
            });
        }

        @Test
        @DisplayName("Không có gì tới hạn thì không đẩy thông báo nào")
        void firesNothingWhenNothingIsDue() {
            when(reminderRepository.findDue(NOW)).thenReturn(List.of());

            assertThat(service.fireDue()).isEmpty();
        }

        @Test
        @DisplayName("Reminder của task hiện tiêu đề task và trỏ về task")
        void aTaskReminderPointsAtTheTask() {
            Task task = new Task("Nộp báo cáo tháng");
            Reminder due = Reminder.forTask(task, NOW, 0);
            when(reminderRepository.findDue(NOW)).thenReturn(List.of(due));

            assertThat(service.fireDue()).singleElement().satisfies(notification -> {
                assertThat(notification.refType()).isEqualTo("TASK");
                assertThat(notification.refId()).isEqualTo(task.getId());
                assertThat(notification.body()).isEqualTo("Đến hạn lúc 12:00");
            });
        }

        @Test
        @DisplayName("T2-12 — reminder quá hạn hơn 24 giờ chuyển sang EXPIRED")
        void staleRemindersAreExpired() {
            Reminder stale = Reminder.forEvent(
                    eventAt(NOW.minus(Duration.ofDays(2)), null), NOW.minus(Duration.ofDays(2)), 0);
            when(reminderRepository.findExpirable(NOW.minus(Reminder.MISSED_WINDOW)))
                    .thenReturn(new ArrayList<>(List.of(stale)));

            assertThat(service.expireStale()).isEqualTo(1);
            assertThat(stale.getStatus()).isEqualTo(ReminderStatus.EXPIRED);
        }

        @Test
        @DisplayName("T2-11 — hoãn ghi bản cũ SNOOZED và lưu bản mới đúng thời điểm")
        void snoozeWritesBothRows() {
            Instant occurrence = NOW.plus(Duration.ofHours(2));
            Reminder original = Reminder.forEvent(eventAt(occurrence, null), occurrence, 60);
            when(reminderRepository.findById("r1")).thenReturn(Optional.of(original));

            Reminder replacement = service.snooze("r1", 10);

            assertThat(original.getStatus()).isEqualTo(ReminderStatus.SNOOZED);
            assertThat(replacement.getStatus()).isEqualTo(ReminderStatus.PENDING);
            assertThat(replacement.getTriggerAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
            assertThat(replacement.occurrenceStart()).isEqualTo(occurrence);
        }

        @Test
        @DisplayName("Không hoãn được reminder đã kết thúc, và số phút phải dương")
        void snoozeRefusesClosedRemindersAndNonPositiveDelays() {
            Reminder dismissed = Reminder.forEvent(eventAt(NOW, null), NOW, 0);
            dismissed.dismiss();
            when(reminderRepository.findById("r1")).thenReturn(Optional.of(dismissed));

            assertThatThrownBy(() -> service.snooze("r1", 10)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> service.snooze("r1", 0)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> service.snooze("missing", 10))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Tắt reminder gắn task thì task chuyển sang DONE (SD-03)")
        void dismissingATaskReminderCompletesTheTask() {
            Task task = new Task("Nộp báo cáo tháng");
            Reminder reminder = Reminder.forTask(task, NOW, 0);
            when(reminderRepository.findById("r1")).thenReturn(Optional.of(reminder));

            service.dismiss("r1");

            assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.DISMISSED);
            assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
            assertThat(task.getCompletedAt()).isEqualTo(NOW);
            verify(taskRepository).save(task);
        }

        @Test
        @DisplayName("Reminder bị lỡ được sắp xếp theo thời điểm, và tắt hàng loạt được")
        void missedRemindersAreOrderedAndCanBeClearedAtOnce() {
            Reminder older = Reminder.forEvent(
                    eventAt(NOW.minus(Duration.ofHours(5)), null), NOW.minus(Duration.ofHours(5)), 0);
            Reminder newer = Reminder.forEvent(
                    eventAt(NOW.minus(Duration.ofHours(1)), null), NOW.minus(Duration.ofHours(1)), 0);
            when(reminderRepository.findMissed(NOW.minus(Reminder.MISSED_WINDOW), NOW))
                    .thenReturn(new ArrayList<>(List.of(newer, older)));

            assertThat(service.findMissed()).containsExactly(older, newer);

            assertThat(service.dismissAll()).isEqualTo(2);
            assertThat(older.getStatus()).isEqualTo(ReminderStatus.DISMISSED);
            assertThat(newer.getStatus()).isEqualTo(ReminderStatus.DISMISSED);
        }
    }
}
