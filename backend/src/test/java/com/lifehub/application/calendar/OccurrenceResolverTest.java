package com.lifehub.application.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lifehub.domain.calendar.Event;
import com.lifehub.domain.calendar.EventException;
import com.lifehub.domain.calendar.EventExceptionRepository;
import com.lifehub.domain.calendar.EventOccurrence;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Applying per-occurrence overrides on top of an expanded series (T2-05, T2-06). */
class OccurrenceResolverTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final EventExceptionRepository exceptionRepository = mock(EventExceptionRepository.class);
    private final OccurrenceResolver resolver =
            new OccurrenceResolver(new RecurrenceExpander(), exceptionRepository);

    private static Instant at(int day, int hour) {
        return ZonedDateTime.of(2026, 1, day, hour, 0, 0, 0, VN).toInstant();
    }

    /** Daily 09:00-10:00 from Monday 05/01/2026. */
    private Event dailyEvent() {
        Event event = new Event("Standup", at(5, 9), at(5, 10));
        event.repeat("FREQ=DAILY");
        event.inZone(VN.getId());
        return event;
    }

    private List<EventOccurrence> resolveWeek(Event event) {
        return resolver.resolve(event, at(5, 0), at(12, 0));
    }

    @Test
    @DisplayName("Không có exception thì mọi instance đều được sinh nguyên bản")
    void withoutOverridesEveryInstanceIsRendered() {
        when(exceptionRepository.findByEventId(anyString())).thenReturn(List.of());

        assertThat(resolveWeek(dailyEvent())).hasSize(7);
    }

    @Test
    @DisplayName("T2-05 — instance bị hủy biến mất khỏi kết quả, các instance khác còn nguyên")
    void aCancelledOccurrenceDisappears() {
        Event event = dailyEvent();
        EventException cancelled = new EventException(event, at(7, 9));
        cancelled.cancel();
        when(exceptionRepository.findByEventId(anyString())).thenReturn(List.of(cancelled));

        List<EventOccurrence> occurrences = resolveWeek(event);

        assertThat(occurrences).hasSize(6);
        assertThat(occurrences)
                .extracting(EventOccurrence::occurrenceStart)
                .doesNotContain(at(7, 9))
                .contains(at(6, 9), at(8, 9));
    }

    @Test
    @DisplayName("T2-06 — instance được sửa giờ dùng giờ mới nhưng giữ mốc gốc làm neo")
    void anOverriddenOccurrenceUsesTheNewTime() {
        Event event = dailyEvent();
        EventException moved = new EventException(event, at(7, 9));
        moved.overrideTime(at(7, 15), at(7, 16));
        moved.overrideTitle("Standup dời chiều");
        when(exceptionRepository.findByEventId(anyString())).thenReturn(List.of(moved));

        EventOccurrence changed = resolveWeek(event).stream()
                .filter(occurrence -> occurrence.occurrenceStart().equals(at(7, 9)))
                .findFirst()
                .orElseThrow();

        assertThat(changed.startAt()).isEqualTo(at(7, 15));
        assertThat(changed.endAt()).isEqualTo(at(7, 16));
        assertThat(changed.title()).isEqualTo("Standup dời chiều");
        assertThat(changed.exception()).isTrue();
    }

    @Test
    @DisplayName("Instance bị dời ra khỏi cửa sổ thì không hiện, dời vào trong thì hiện")
    void movingAnOccurrenceAcrossTheWindowBoundaryIsHonoured() {
        Event event = dailyEvent();
        EventException pushedOut = new EventException(event, at(6, 9));
        pushedOut.overrideTime(at(20, 9), at(20, 10));
        when(exceptionRepository.findByEventId(anyString())).thenReturn(List.of(pushedOut));

        List<EventOccurrence> week = resolver.resolve(event, at(5, 0), at(12, 0));
        assertThat(week)
                .as("instance ngày 06 đã bị dời sang ngày 20 nên không còn thuộc tuần này")
                .extracting(EventOccurrence::startAt)
                .doesNotContain(at(6, 9));

        List<EventOccurrence> later = resolver.resolve(event, at(20, 0), at(21, 0));
        assertThat(later)
                .as("và phải xuất hiện ở cửa sổ chứa giờ mới, dù quy luật lặp không sinh ra nó ở đó")
                .extracting(EventOccurrence::startAt)
                .contains(at(20, 9));
    }

    @Test
    @DisplayName("Event không lặp trải dài nhiều ngày vẫn hiện ở cửa sổ nằm giữa nó")
    void aLongOneOffEventShowsUpInAWindowItMerelyOverlaps() {
        Event holiday = new Event("Nghỉ Tết", at(1, 0), at(10, 0));
        when(exceptionRepository.findByEventId(anyString())).thenReturn(List.of());

        assertThat(resolver.resolve(holiday, at(5, 0), at(6, 0)))
                .as("cửa sổ ngày 05 nằm hoàn toàn bên trong sự kiện, không chứa mốc bắt đầu của nó")
                .hasSize(1);
    }

    @Test
    @DisplayName("Instance lặp bắt đầu trước cửa sổ nhưng còn kéo dài sang trong vẫn được sinh")
    void anInstanceStraddlingTheWindowStartIsKept() {
        Event event = new Event("Ca trực", at(5, 8), at(5, 12));
        event.repeat("FREQ=DAILY");
        event.inZone(VN.getId());
        when(exceptionRepository.findByEventId(anyString())).thenReturn(List.of());

        assertThat(resolver.resolve(event, at(6, 10), at(6, 11)))
                .as("ca trực ngày 06 bắt đầu lúc 08:00 và vẫn đang diễn ra lúc 10:00")
                .hasSize(1);
    }

    @Test
    @DisplayName("Nhiều event được nạp exception trong một lượt truy vấn")
    void overridesForManyEventsAreLoadedInOneQuery() {
        Event first = dailyEvent();
        Event second = new Event("Retro", at(5, 14), at(5, 15));
        second.repeat("FREQ=WEEKLY");
        second.inZone(VN.getId());

        EventException cancelled = new EventException(first, at(6, 9));
        cancelled.cancel();
        when(exceptionRepository.findByEventIds(List.of(first.getId(), second.getId())))
                .thenReturn(List.of(cancelled));

        List<EventOccurrence> resolved =
                resolver.resolveAll(List.of(first, second), at(5, 0), at(12, 0));

        assertThat(resolved).hasSize(7 - 1 + 1);
        assertThat(resolved)
                .filteredOn(occurrence -> occurrence.eventId().equals(first.getId()))
                .hasSize(6);
    }
}
