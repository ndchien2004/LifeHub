package com.lifehub.application.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lifehub.domain.common.ValidationException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Recurrence expansion (T2-01 to T2-04, T2-07).
 *
 * <p>Every case is anchored to a real calendar date rather than "now", so a test that passes today
 * still passes in March - a recurrence bug that only shows up in a month with 30 days is exactly
 * the kind this suite exists to catch.
 */
class RecurrenceExpanderTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Monday 05/01/2026, 09:00 local. */
    private static final Instant SEED = at(2026, 1, 5, 9, 0);

    private final RecurrenceExpander expander = new RecurrenceExpander();

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, VN).toInstant();
    }

    private List<Instant> expandYear(String rrule) {
        return expander.expand(rrule, SEED, VN, at(2026, 1, 1, 0, 0), at(2027, 1, 1, 0, 0));
    }

    @Nested
    @DisplayName("Mở rộng RRULE")
    class Expansion {

        @Test
        @DisplayName("T2-01 — FREQ=DAILY;COUNT=5 sinh đúng 5 instance liên tiếp")
        void dailyWithCountProducesExactlyThatMany() {
            List<Instant> occurrences = expandYear("FREQ=DAILY;COUNT=5");

            assertThat(occurrences).hasSize(5);
            assertThat(occurrences.get(0)).isEqualTo(SEED);
            assertThat(occurrences.get(4)).isEqualTo(at(2026, 1, 9, 9, 0));
        }

        @Test
        @DisplayName("T2-02 — FREQ=WEEKLY;BYDAY=MO,WE;UNTIL chỉ rơi vào thứ 2 và thứ 4")
        void weeklyByDayOnlyHitsTheNamedWeekdays() {
            List<Instant> occurrences = expandYear("FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20260201T000000Z");

            assertThat(occurrences)
                    .as("05/01 là thứ 2; các mốc còn lại xen kẽ thứ 2 và thứ 4 cho tới UNTIL")
                    .containsExactly(
                            at(2026, 1, 5, 9, 0),
                            at(2026, 1, 7, 9, 0),
                            at(2026, 1, 12, 9, 0),
                            at(2026, 1, 14, 9, 0),
                            at(2026, 1, 19, 9, 0),
                            at(2026, 1, 21, 9, 0),
                            at(2026, 1, 26, 9, 0),
                            at(2026, 1, 28, 9, 0));
        }

        @Test
        @DisplayName("T2-03 — FREQ=MONTHLY;BYMONTHDAY=31 bỏ qua các tháng không có ngày 31")
        void monthlyOnTheThirtyFirstSkipsShortMonths() {
            List<Instant> occurrences = expandYear("FREQ=MONTHLY;BYMONTHDAY=31");

            assertThat(occurrences)
                    .as("tháng 2, 4, 6, 9, 11 không có ngày 31 nên bị bỏ qua, không lùi về ngày cuối")
                    .containsExactly(
                            at(2026, 1, 31, 9, 0),
                            at(2026, 3, 31, 9, 0),
                            at(2026, 5, 31, 9, 0),
                            at(2026, 7, 31, 9, 0),
                            at(2026, 8, 31, 9, 0),
                            at(2026, 10, 31, 9, 0),
                            at(2026, 12, 31, 9, 0));
        }

        @Test
        @DisplayName("T2-04 — RRULE vô hạn dừng ở 500 instance thay vì treo")
        void unboundedRuleStopsAtTheSafetyCap() {
            List<Instant> occurrences =
                    expander.expand("FREQ=SECONDLY", SEED, VN, SEED, at(2100, 1, 1, 0, 0));

            assertThat(occurrences)
                    .as("FREQ=SECONDLY là RFC 5545 hợp lệ và sẽ sinh hàng triệu mốc nếu không chặn")
                    .hasSize(RecurrenceExpander.MAX_INSTANCES);
        }

        @Test
        @DisplayName("INTERVAL được tôn trọng")
        void honoursInterval() {
            assertThat(expandYear("FREQ=DAILY;INTERVAL=3;COUNT=4"))
                    .containsExactly(
                            at(2026, 1, 5, 9, 0),
                            at(2026, 1, 8, 9, 0),
                            at(2026, 1, 11, 9, 0),
                            at(2026, 1, 14, 9, 0));
        }

        @Test
        @DisplayName("COUNT tính từ mốc gốc, không đếm lại khi cửa sổ mở muộn hơn")
        void countIsMeasuredFromTheSeedNotTheWindow() {
            List<Instant> occurrences = expander.expand(
                    "FREQ=DAILY;COUNT=5", SEED, VN, at(2026, 1, 7, 0, 0), at(2027, 1, 1, 0, 0));

            assertThat(occurrences)
                    .as("cửa sổ mở ngày 07/01 nên chỉ còn 3 trong 5 lần của chuỗi")
                    .containsExactly(
                            at(2026, 1, 7, 9, 0), at(2026, 1, 8, 9, 0), at(2026, 1, 9, 9, 0));
        }

        @Test
        @DisplayName("Khoảng là nửa mở [from, to) nên ô lịch liền kề không vẽ trùng instance")
        void windowIsHalfOpen() {
            List<Instant> occurrences =
                    expander.expand("FREQ=DAILY", SEED, VN, SEED, at(2026, 1, 7, 9, 0));

            assertThat(occurrences).containsExactly(at(2026, 1, 5, 9, 0), at(2026, 1, 6, 9, 0));
        }

        @Test
        @DisplayName("Không có RRULE thì chỉ trả về chính mốc bắt đầu, nếu nó nằm trong khoảng")
        void noRuleYieldsTheSeedAlone() {
            assertThat(expander.expand(null, SEED, VN, at(2026, 1, 1, 0, 0), at(2026, 2, 1, 0, 0)))
                    .containsExactly(SEED);
            assertThat(expander.expand(null, SEED, VN, at(2026, 2, 1, 0, 0), at(2026, 3, 1, 0, 0)))
                    .isEmpty();
        }

        @Test
        @DisplayName("RRULE sai cú pháp báo lỗi có tên trường, không ném exception thô")
        void malformedRuleIsRejectedAsAFieldError() {
            assertThatThrownBy(() -> expander.validate("KHONG-PHAI-RRULE"))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> assertThat(((ValidationException) e).getField()).isEqualTo("rrule"));
        }

        @Test
        @DisplayName("Tiền tố RRULE: được chấp nhận cùng với dạng chỉ có giá trị")
        void acceptsThePropertyPrefix() {
            assertThat(expandYear("RRULE:FREQ=DAILY;COUNT=2")).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Mốc kế tiếp")
    class NextAfter {

        @Test
        @DisplayName("Tìm được mốc kế tiếp của chuỗi hàng tuần")
        void findsTheFollowingWeeklyOccurrence() {
            assertThat(expander.nextAfter("FREQ=WEEKLY", SEED, VN, SEED))
                    .contains(at(2026, 1, 12, 9, 0));
        }

        @Test
        @DisplayName("Chuỗi đã kết thúc theo UNTIL thì không còn mốc nào")
        void returnsEmptyOnceTheSeriesHasEnded() {
            assertThat(expander.nextAfter("FREQ=DAILY;UNTIL=20260106T000000Z", SEED, VN, SEED))
                    .isEmpty();
        }

        @Test
        @DisplayName("Tìm được mốc cách xa hơn một tháng, nhờ mở rộng cửa sổ dần")
        void widensTheSearchWindowUntilItFindsOne() {
            assertThat(expander.nextAfter("FREQ=YEARLY", SEED, VN, SEED))
                    .contains(at(2027, 1, 5, 9, 0));
        }
    }

    @Nested
    @DisplayName("Cắt chuỗi và tiêu thụ COUNT")
    class SplitAndConsume {

        @Test
        @DisplayName("T2-07 — cắt chuỗi đặt UNTIL trước mốc cắt 1 giây, chuỗi mới bắt đầu đúng mốc")
        void splitClosesTheMasterJustBeforeTheCut() {
            Instant cut = at(2026, 1, 19, 9, 0);

            RecurrenceExpander.Split split = expander.splitAt("FREQ=WEEKLY", SEED, VN, cut);

            List<Instant> head = expander.expand(
                    split.masterRule(), SEED, VN, at(2026, 1, 1, 0, 0), at(2026, 3, 1, 0, 0));
            List<Instant> tail = expander.expand(
                    split.followingRule(), cut, VN, at(2026, 1, 1, 0, 0), at(2026, 3, 1, 0, 0));

            assertThat(head)
                    .as("chuỗi cũ dừng ngay trước mốc cắt")
                    .containsExactly(at(2026, 1, 5, 9, 0), at(2026, 1, 12, 9, 0));
            assertThat(tail).as("mốc cắt thuộc về chuỗi mới, không sinh trùng").startsWith(cut);
            assertThat(head).doesNotContainAnyElementsOf(tail);
        }

        @Test
        @DisplayName("Cắt chuỗi có COUNT thì chia số lần cho hai nửa thay vì nhân đôi chuỗi")
        void splitDividesTheCountBetweenBothHalves() {
            Instant cut = at(2026, 1, 19, 9, 0);

            RecurrenceExpander.Split split = expander.splitAt("FREQ=WEEKLY;COUNT=5", SEED, VN, cut);

            List<Instant> head = expander.expand(
                    split.masterRule(), SEED, VN, at(2026, 1, 1, 0, 0), at(2027, 1, 1, 0, 0));
            List<Instant> tail = expander.expand(
                    split.followingRule(), cut, VN, at(2026, 1, 1, 0, 0), at(2027, 1, 1, 0, 0));

            assertThat(head).hasSize(2);
            assertThat(tail).hasSize(3);
            assertThat(head.size() + tail.size()).as("tổng vẫn đúng 5 lần như chuỗi gốc").isEqualTo(5);
        }

        @Test
        @DisplayName("consumeOne giảm COUNT một đơn vị và cạn khi chỉ còn một lần")
        void consumeOneSpendsTheCountOneInstanceAtATime() {
            Optional<String> second = expander.consumeOne("FREQ=DAILY;COUNT=3");
            assertThat(second).contains("FREQ=DAILY;COUNT=2");

            assertThat(expander.consumeOne(second.orElseThrow())).contains("FREQ=DAILY;COUNT=1");
            assertThat(expander.consumeOne("FREQ=DAILY;COUNT=1"))
                    .as("lần cuối cùng không sinh instance kế tiếp nữa")
                    .isEmpty();
        }

        @Test
        @DisplayName("consumeOne giữ nguyên quy luật không có COUNT")
        void consumeOneLeavesUnboundedRulesAlone() {
            assertThat(expander.consumeOne("FREQ=WEEKLY;BYDAY=MO")).contains("FREQ=WEEKLY;BYDAY=MO");
            assertThat(expander.consumeOne(null)).isEmpty();
        }
    }
}
