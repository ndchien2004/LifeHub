package com.lifehub.domain.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lifehub.domain.common.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Event invariants (T2-08). */
class EventTest {

    private static final Instant START = Instant.parse("2026-09-15T07:00:00Z");
    private static final Instant END = Instant.parse("2026-09-15T08:00:00Z");

    private Event anEvent() {
        return new Event("Họp review sprint", START, END);
    }

    @Nested
    @DisplayName("Khoảng thời gian")
    class Range {

        @Test
        @DisplayName("T2-08 — kết thúc bằng hoặc trước lúc bắt đầu bị từ chối")
        void rejectsAnEndAtOrBeforeTheStart() {
            assertThatThrownBy(() -> new Event("Họp", START, START))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> assertThat(((ValidationException) e).getField()).isEqualTo("endAt"));

            assertThatThrownBy(() -> new Event("Họp", END, START))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("Thiếu mốc bắt đầu hoặc kết thúc báo đúng tên trường")
        void namesTheMissingField() {
            assertThatThrownBy(() -> new Event("Họp", null, END))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> assertThat(((ValidationException) e).getField()).isEqualTo("startAt"));

            assertThatThrownBy(() -> new Event("Họp", START, null))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> assertThat(((ValidationException) e).getField()).isEqualTo("endAt"));
        }

        @Test
        @DisplayName("Dời sự kiện giữ nguyên độ dài")
        void movingPreservesTheDuration() {
            Event event = anEvent();

            event.moveTo(START.plus(Duration.ofDays(1)));

            assertThat(event.getStartAt()).isEqualTo(START.plus(Duration.ofDays(1)));
            assertThat(event.duration()).isEqualTo(Duration.ofHours(1));
        }

        @Test
        @DisplayName("Chồng lấn tính theo khoảng nửa mở, chạm biên không tính là chồng")
        void overlapIsHalfOpen() {
            Event event = anEvent();

            assertThat(event.overlaps(START.minusSeconds(1), START.plusSeconds(1))).isTrue();
            assertThat(event.overlaps(END, END.plusSeconds(3600)))
                    .as("sự kiện kế tiếp bắt đầu đúng lúc sự kiện này kết thúc thì không xung đột")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Tiêu đề, lặp lại và múi giờ")
    class Attributes {

        @Test
        @DisplayName("Tiêu đề rỗng bị từ chối, khoảng trắng thừa được cắt")
        void titleIsRequiredAndTrimmed() {
            assertThatThrownBy(() -> new Event("   ", START, END))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> assertThat(((ValidationException) e).getField()).isEqualTo("title"));

            assertThat(new Event("  Họp  ", START, END).getTitle()).isEqualTo("Họp");
        }

        @Test
        @DisplayName("RRULE rỗng được coi là không lặp")
        void blankRruleMeansNoRepetition() {
            Event event = anEvent();

            event.repeat("   ");
            assertThat(event.isRecurring()).isFalse();
            assertThat(event.getRrule()).isNull();

            event.repeat("FREQ=WEEKLY");
            assertThat(event.isRecurring()).isTrue();
        }

        @Test
        @DisplayName("Múi giờ không hợp lệ bị từ chối, để trống thì dùng mặc định")
        void timezoneIsValidatedWithAFallback() {
            Event event = anEvent();

            assertThatThrownBy(() -> event.inZone("Sao/Hoa"))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> assertThat(((ValidationException) e).getField()).isEqualTo("timezone"));

            event.inZone(null);
            assertThat(event.zone()).isEqualTo(ZoneId.of(Event.DEFAULT_TIMEZONE));

            event.inZone("Europe/Paris");
            assertThat(event.zone()).isEqualTo(ZoneId.of("Europe/Paris"));
        }

        @Test
        @DisplayName("Xóa mềm rồi khôi phục")
        void softDeleteAndRestore() {
            Event event = anEvent();

            event.softDelete(START);
            assertThat(event.isDeleted()).isTrue();

            event.restore();
            assertThat(event.isDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("Instance của chuỗi lặp")
    class Occurrences {

        @Test
        @DisplayName("Instance kế thừa độ dài của event master")
        void occurrenceInheritsTheMasterDuration() {
            Instant slot = Instant.parse("2026-09-22T07:00:00Z");

            EventOccurrence occurrence = EventOccurrence.of(anEvent(), slot);

            assertThat(occurrence.startAt()).isEqualTo(slot);
            assertThat(occurrence.endAt()).isEqualTo(slot.plus(Duration.ofHours(1)));
            assertThat(occurrence.occurrenceStart()).isEqualTo(slot);
            assertThat(occurrence.exception()).isFalse();
        }

        @Test
        @DisplayName("Ghi đè giờ giữ nguyên mốc gốc làm neo, và giữ độ dài nếu chỉ đổi giờ bắt đầu")
        void overrideKeepsTheOriginalAnchor() {
            Event event = anEvent();
            Instant slot = Instant.parse("2026-09-22T07:00:00Z");
            Instant moved = Instant.parse("2026-09-22T09:00:00Z");

            EventException override = new EventException(event, slot);
            override.overrideTime(moved, null);

            EventOccurrence occurrence = EventOccurrence.of(event, slot).withOverrides(override);

            assertThat(occurrence.occurrenceStart())
                    .as("mốc gốc là thứ định danh instance, kể cả sau khi bị dời")
                    .isEqualTo(slot);
            assertThat(occurrence.startAt()).isEqualTo(moved);
            assertThat(occurrence.endAt()).isEqualTo(moved.plus(Duration.ofHours(1)));
            assertThat(occurrence.exception()).isTrue();
        }

        @Test
        @DisplayName("Hủy một instance xóa mọi giá trị ghi đè trước đó")
        void cancellingClearsAnyOverrides() {
            EventException override = new EventException(anEvent(), START);
            override.overrideTitle("Đổi tên");

            override.cancel();

            assertThat(override.isCancelled()).isTrue();
            assertThat(override.getNewTitle()).isNull();
            assertThat(override.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("Exception chưa ghi đè gì thì được coi là rỗng, không đáng lưu")
        void anUntouchedOverrideIsEmpty() {
            assertThat(new EventException(anEvent(), START).isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Instance chỉ được coi là xung đột khi thực sự chồng thời gian")
        void collisionRequiresRealOverlap() {
            Event event = anEvent();
            EventOccurrence first = EventOccurrence.of(event, START);
            EventOccurrence adjacent = EventOccurrence.of(event, END);

            assertThat(first.collidesWith(adjacent)).isFalse();
            assertThat(first.collidesWith(EventOccurrence.of(event, START.plusSeconds(1800)))).isTrue();
        }

        @Test
        @DisplayName("Tạo instance không ném lỗi khi event kéo dài nhiều ngày")
        void handlesMultiDayEvents() {
            Event multiDay = new Event("Nghỉ phép", START, START.plus(Duration.ofDays(5)));

            assertThatCode(() -> EventOccurrence.of(multiDay, START)).doesNotThrowAnyException();
            assertThat(EventOccurrence.of(multiDay, START).endAt())
                    .isEqualTo(START.plus(Duration.ofDays(5)));
        }
    }
}
