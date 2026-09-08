package com.lifehub.api.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifehub.domain.calendar.Event;
import com.lifehub.domain.calendar.EventException;
import com.lifehub.domain.calendar.EventExceptionRepository;
import com.lifehub.domain.calendar.EventRepository;
import com.lifehub.support.ApiIntegrationTest;
import com.lifehub.support.TestDatabase;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T2-15 — a month view holding 200 instances, rendered in under 500 ms.
 *
 * <p>What this really guards is the absence of an N+1. Each instance carries its overrides and its
 * reminders, and fetching those per instance rather than per window is the failure that would only
 * show up on a real calendar, long after the code looked correct on a handful of events.
 */
class CalendarPerformanceIT extends ApiIntegrationTest {

    private static final String DATABASE_URL = TestDatabase.freshUrl();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration BUDGET = Duration.ofMillis(500);

    /** 40 daily series over a 7 day window, plus 20 stand-alone events: 260 instances. */
    private static final int SERIES_COUNT = 40;
    private static final int WINDOW_DAYS = 7;
    private static final int ONE_OFF_COUNT = 20;
    private static final int EXPECTED_INSTANCES =
            SERIES_COUNT * WINDOW_DAYS - SERIES_COUNT + ONE_OFF_COUNT;

    private static boolean seeded;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventExceptionRepository exceptionRepository;

    private static OffsetDateTime windowStart() {
        return ZonedDateTime.now(VN)
                .truncatedTo(ChronoUnit.DAYS)
                .plusDays(30)
                .toOffsetDateTime();
    }

    @BeforeAll
    static void resetSeedFlag() {
        seeded = false;
    }

    /**
     * Written straight through the repositories rather than over HTTP.
     *
     * <p>Seeding through the API would also generate 90 days of reminders per series, which is a
     * different cost and would make the setup dominate the measurement.
     */
    private void seed() {
        if (seeded) {
            return;
        }
        OffsetDateTime base = windowStart();

        for (int i = 0; i < SERIES_COUNT; i++) {
            OffsetDateTime start = base.plusMinutes(15L * i);
            Event event = new Event("Chuỗi " + i, start.toInstant(), start.plusMinutes(30).toInstant());
            event.inZone(VN.getId());
            event.repeat("FREQ=DAILY;COUNT=" + WINDOW_DAYS);
            event.relocate("Phòng " + i);
            Event saved = eventRepository.save(event);

            // One cancelled and one moved instance per series, so the override path is exercised
            // at the same scale as the expansion path.
            EventException cancelled = new EventException(saved, start.plusDays(1).toInstant());
            cancelled.cancel();
            exceptionRepository.save(cancelled);

            EventException moved = new EventException(saved, start.plusDays(2).toInstant());
            moved.overrideTime(start.plusDays(2).plusHours(3).toInstant(), start.plusDays(2).plusHours(4).toInstant());
            exceptionRepository.save(moved);
        }

        for (int i = 0; i < ONE_OFF_COUNT; i++) {
            OffsetDateTime start = base.plusHours(12).plusMinutes(5L * i);
            Event event = new Event("Lẻ " + i, start.toInstant(), start.plusMinutes(20).toInstant());
            event.inZone(VN.getId());
            eventRepository.save(event);
        }
        seeded = true;
    }

    @Test
    @DisplayName("T2-15 — lịch tháng với hơn 200 instance trả về trong ≤ 500 ms")
    void aBusyMonthRendersInsideTheBudget() throws Exception {
        seed();
        OffsetDateTime from = windowStart();
        OffsetDateTime to = from.plusDays(WINDOW_DAYS + 1);

        // One untimed call first: the measurement should reflect steady state, not JIT warm-up
        // and first-connection cost.
        JsonNode warmUp = fetch(from, to);
        assertThat(warmUp.size())
                .as("%d chuỗi x %d ngày, trừ %d instance bị hủy, cộng %d event lẻ",
                        SERIES_COUNT, WINDOW_DAYS, SERIES_COUNT, ONE_OFF_COUNT)
                .isEqualTo(EXPECTED_INSTANCES)
                .isGreaterThan(200);

        long elapsedMillis = System.nanoTime();
        JsonNode items = fetch(from, to);
        elapsedMillis = (System.nanoTime() - elapsedMillis) / 1_000_000;

        assertThat(items.size()).isEqualTo(warmUp.size());
        assertThat(elapsedMillis)
                .as("lịch %d instance mất %d ms, ngân sách %d ms", items.size(), elapsedMillis, BUDGET.toMillis())
                .isLessThanOrEqualTo(BUDGET.toMillis());
    }

    private JsonNode fetch(OffsetDateTime from, OffsetDateTime to) throws Exception {
        return data(mockMvc.perform(authed(get("/api/v1/events")
                                .param("from", from.toString())
                                .param("to", to.toString())
                                .param("includeTasks", "true")))
                        .andExpect(status().isOk())
                        .andReturn());
    }
}
