package com.lifehub.api.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifehub.support.ApiIntegrationTest;
import com.lifehub.support.TestDatabase;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Full calendar lifecycle over HTTP (T2-13, T2-14).
 *
 * <p>Every date is expressed as an offset in days from midnight today, for two reasons. Reminder
 * generation only reaches 90 days ahead, so a hard-coded calendar date would silently stop
 * producing reminders once that date drifts past the horizon. And the class shares one database
 * across its methods, so each test is given its own disjoint band of days and cannot see another
 * test's events.
 */
class EventApiIT extends ApiIntegrationTest {

    private static final String DATABASE_URL = TestDatabase.freshUrl();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final OffsetDateTime TODAY =
            ZonedDateTime.now(VN).truncatedTo(ChronoUnit.DAYS).toOffsetDateTime();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    /** Midnight, {@code days} days from today. */
    private static OffsetDateTime day(int days) {
        return TODAY.plusDays(days);
    }

    private static OffsetDateTime at(int days, int hour, int minute) {
        return TODAY.plusDays(days).plusHours(hour).plusMinutes(minute);
    }

    /**
     * A timestamp field of a response item, as an instant.
     *
     * <p>Compared as parsed values rather than strings: {@code OffsetDateTime.toString()} drops
     * zero seconds while the serialised response keeps them, so two identical moments would
     * otherwise fail to match on their text alone.
     */
    private static OffsetDateTime time(JsonNode item, String field) {
        return OffsetDateTime.parse(item.path(field).asText());
    }

    private String createEvent(Map<String, Object> body) throws Exception {
        return data(mockMvc.perform(authed(post("/api/v1/events")).content(json(body)))
                        .andExpect(status().isCreated())
                        .andReturn())
                .path("id")
                .asText();
    }

    private JsonNode listBetween(OffsetDateTime from, OffsetDateTime to) throws Exception {
        return data(mockMvc.perform(authed(get("/api/v1/events")
                                .param("from", from.toString())
                                .param("to", to.toString())
                                .param("includeTasks", "false")))
                        .andExpect(status().isOk())
                        .andReturn());
    }

    /**
     * The occurrence anchor goes into the path verbatim.
     *
     * <p>Built as a {@link URI} rather than a URL template so MockMvc does not re-encode the
     * {@code +07:00} offset, which is exactly the round trip the endpoint has to survive.
     */
    private URI occurrencePath(String eventId, OffsetDateTime occurrenceStart) {
        return URI.create("/api/v1/events/" + eventId + "/occurrences/" + occurrenceStart);
    }

    /**
     * A five week series at 09:00 from {@code startDay}, with a 15 minute reminder.
     *
     * <p>Bounded with COUNT on purpose: an open ended weekly rule keeps producing instances for
     * ever and would show up in every later test's window, since all methods here share one
     * database.
     */
    private Map<String, Object> weeklySeries(int startDay) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Standup tuần");
        body.put("location", "Phòng họp A");
        body.put("startAt", at(startDay, 9, 0).toString());
        body.put("endAt", at(startDay, 9, 30).toString());
        body.put("rrule", "FREQ=WEEKLY;COUNT=5");
        body.put("timezone", VN.getId());
        body.put("reminderOffsets", List.of(15));
        return body;
    }

    @Test
    @DisplayName("CRUD đầy đủ cho event không lặp")
    void supportsTheFullLifecycleOfAOneOffEvent() throws Exception {
        String id = createEvent(Map.of(
                "title", "Khám sức khỏe",
                "description", "Mang theo sổ khám",
                "location", "Bệnh viện Q1",
                "startAt", at(10, 8, 0).toString(),
                "endAt", at(10, 9, 0).toString(),
                "reminderOffsets", List.of(60, 1440)));

        mockMvc.perform(authed(get("/api/v1/events/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Khám sức khỏe"))
                .andExpect(jsonPath("$.data.location").value("Bệnh viện Q1"))
                .andExpect(jsonPath("$.data.rrule").doesNotExist())
                .andExpect(jsonPath("$.data.reminderOffsets", org.hamcrest.Matchers.contains(60, 1440)));

        mockMvc.perform(authed(patch("/api/v1/events/" + id))
                        .content(json(Map.of("title", "Khám sức khỏe định kỳ", "location", "Bệnh viện Q3"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Khám sức khỏe định kỳ"))
                .andExpect(jsonPath("$.data.location").value("Bệnh viện Q3"));

        assertThat(listBetween(day(10), day(11)))
                .singleElement()
                .satisfies(item -> assertThat(item.path("reminders")).hasSize(2));

        mockMvc.perform(authed(delete("/api/v1/events/" + id))).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/events/" + id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        assertThat(listBetween(day(10), day(11)))
                .as("xóa chuỗi cũng gỡ luôn reminder chưa bắn")
                .isEmpty();
    }

    @Test
    @DisplayName("T2-13 — event lặp hàng tuần trả đúng số instance trong khoảng truy vấn")
    void expandsARepeatingSeriesAcrossTheRequestedWindow() throws Exception {
        createEvent(weeklySeries(21));

        JsonNode window = listBetween(day(20), day(50));

        assertThat(window)
                .as("chuỗi bắt đầu ngày 21 và lặp mỗi 7 ngày: 21, 28, 35, 42, 49")
                .hasSize(5);
        assertThat(window.get(0).path("isRecurring").asBoolean()).isTrue();
        assertThat(window.get(0).path("isException").asBoolean()).isFalse();
        assertThat(window.get(0).path("kind").asText()).isEqualTo("EVENT");
        assertThat(window.get(0).path("reminders")).hasSize(1);
        assertThat(window.get(0).path("reminders").get(0).path("offsetMinutes").asInt()).isEqualTo(15);
        assertThat(window)
                .as("mỗi instance có reminder riêng, không dùng chung một bản ghi")
                .extracting(item -> item.path("reminders").get(0).path("id").asText())
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("T2-14 — xóa một instance tạo exception, các instance khác còn nguyên")
    void deletingOneOccurrenceLeavesTheRestOfTheSeries() throws Exception {
        String id = createEvent(weeklySeries(61));

        mockMvc.perform(authed(delete(occurrencePath(id, at(68, 9, 0))))).andExpect(status().isOk());

        JsonNode window = listBetween(day(60), day(90));

        assertThat(window).hasSize(4);
        assertThat(window)
                .extracting(item -> time(item, "occurrenceStart"))
                .doesNotContain(at(68, 9, 0));

        mockMvc.perform(authed(get("/api/v1/events/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rrule")
                        .value(org.hamcrest.Matchers.equalTo("FREQ=WEEKLY;COUNT=5")));
    }

    @Test
    @DisplayName("Sửa với scope THIS_ONLY chỉ đổi đúng instance đó")
    void thisOnlyChangesASingleOccurrence() throws Exception {
        String id = createEvent(weeklySeries(101));

        mockMvc.perform(authed(patch(occurrencePath(id, at(108, 9, 0))))
                        .content(json(Map.of(
                                "scope", "THIS_ONLY",
                                "title", "Standup dời chiều",
                                "startAt", at(108, 15, 0).toString(),
                                "endAt", at(108, 15, 30).toString()))))
                .andExpect(status().isOk());

        JsonNode window = listBetween(day(100), day(130));

        assertThat(window).hasSize(5);
        JsonNode moved = window.get(1);
        assertThat(moved.path("title").asText()).isEqualTo("Standup dời chiều");
        assertThat(moved.path("isException").asBoolean()).isTrue();
        assertThat(time(moved, "startAt")).isEqualTo(at(108, 15, 0));
        assertThat(time(moved, "occurrenceStart"))
                .as("mốc gốc vẫn là neo của instance sau khi bị dời")
                .isEqualTo(at(108, 9, 0));
        assertThat(window.get(2).path("title").asText())
                .as("các instance khác không bị ảnh hưởng")
                .isEqualTo("Standup tuần");
    }

    @Test
    @DisplayName("Sửa với scope THIS_AND_FOLLOWING cắt chuỗi cũ và tạo chuỗi mới")
    void thisAndFollowingSplitsTheSeries() throws Exception {
        String id = createEvent(weeklySeries(141));

        String newId = data(mockMvc.perform(authed(patch(occurrencePath(id, at(155, 9, 0))))
                                .content(json(Map.of(
                                        "scope", "THIS_AND_FOLLOWING",
                                        "title", "Standup buổi chiều",
                                        "location", "Phòng họp B"))))
                        .andExpect(status().isOk())
                        .andReturn())
                .path("id")
                .asText();

        assertThat(newId).isNotEqualTo(id);

        mockMvc.perform(authed(get("/api/v1/events/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Standup tuần"))
                .andExpect(jsonPath("$.data.rrule")
                        .value(org.hamcrest.Matchers.startsWith("FREQ=WEEKLY;UNTIL=")));

        JsonNode window = listBetween(day(140), day(170));

        assertThat(window).as("vẫn đủ 5 lần, không mất và không trùng lặp").hasSize(5);
        assertThat(window)
                .extracting(item -> item.path("title").asText())
                .containsExactly(
                        "Standup tuần",
                        "Standup tuần",
                        "Standup buổi chiều",
                        "Standup buổi chiều",
                        "Standup buổi chiều");
        assertThat(window.get(2).path("eventId").asText()).isEqualTo(newId);
        assertThat(window.get(2).path("location").asText()).isEqualTo("Phòng họp B");
        assertThat(window.get(1).path("eventId").asText()).isEqualTo(id);
    }

    @Test
    @DisplayName("Sửa với scope ALL đổi toàn bộ chuỗi")
    void allChangesEveryOccurrence() throws Exception {
        String id = createEvent(weeklySeries(181));

        mockMvc.perform(authed(patch(occurrencePath(id, at(188, 9, 0))))
                        .content(json(Map.of("scope", "ALL", "title", "Daily sync"))))
                .andExpect(status().isOk());

        assertThat(listBetween(day(180), day(210)))
                .extracting(item -> item.path("title").asText())
                .containsOnly("Daily sync");
    }

    @Test
    @DisplayName("Event cả ngày lưu theo quy ước 00:00 tới 00:00 hôm sau (M-13)")
    void anAllDayEventSpansMidnightToMidnight() throws Exception {
        String id = createEvent(Map.of(
                "title", "Nghỉ lễ",
                "startAt", day(220).toString(),
                "endAt", day(221).toString(),
                "allDay", true));

        mockMvc.perform(authed(get("/api/v1/events/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allDay").value(true));

        assertThat(listBetween(day(219), day(223))).hasSize(1);
    }

    @Test
    @DisplayName("FR-CAL-11 — hai event trùng giờ được đánh dấu xung đột")
    void overlappingEventsAreFlagged() throws Exception {
        createEvent(Map.of(
                "title", "Họp kế hoạch",
                "startAt", at(230, 14, 0).toString(),
                "endAt", at(230, 15, 0).toString()));
        createEvent(Map.of(
                "title", "Phỏng vấn ứng viên",
                "startAt", at(230, 14, 30).toString(),
                "endAt", at(230, 15, 30).toString()));

        JsonNode window = listBetween(day(230), day(231));

        assertThat(window).hasSize(2);
        assertThat(window).allMatch(item -> item.path("hasConflict").asBoolean());

        mockMvc.perform(authed(get("/api/v1/events/conflicts")
                        .param("from", at(230, 14, 45).toString())
                        .param("to", at(230, 15, 15).toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    @DisplayName("Event sát nhau nhưng không chồng giờ thì không bị coi là xung đột")
    void adjacentEventsDoNotConflict() throws Exception {
        createEvent(Map.of(
                "title", "Ca sáng",
                "startAt", at(240, 8, 0).toString(),
                "endAt", at(240, 12, 0).toString()));
        createEvent(Map.of(
                "title", "Ca chiều",
                "startAt", at(240, 12, 0).toString(),
                "endAt", at(240, 17, 0).toString()));

        assertThat(listBetween(day(240), day(241)))
                .hasSize(2)
                .allMatch(item -> !item.path("hasConflict").asBoolean());
    }

    @Test
    @DisplayName("FR-CAL-10 — task có hạn chót xuất hiện trên lịch dưới dạng riêng")
    void taskDeadlinesAppearOnTheGrid() throws Exception {
        String taskId = data(mockMvc.perform(authed(post("/api/v1/tasks"))
                                .content(json(Map.of(
                                        "title", "Nộp báo cáo quý",
                                        "dueAt", at(250, 17, 0).toString()))))
                        .andExpect(status().isCreated())
                        .andReturn())
                .path("id")
                .asText();

        JsonNode withTasks = data(mockMvc.perform(authed(get("/api/v1/events")
                                .param("from", day(250).toString())
                                .param("to", day(251).toString())))
                        .andExpect(status().isOk())
                        .andReturn());

        assertThat(withTasks).hasSize(1);
        assertThat(withTasks.get(0).path("kind").asText()).isEqualTo("TASK");
        assertThat(withTasks.get(0).path("linkedTaskId").asText()).isEqualTo(taskId);
        assertThat(withTasks.get(0).path("eventId").isMissingNode())
                .as("mục task không có eventId, đó là dấu hiệu để lịch vẽ khác kiểu")
                .isTrue();
        assertThat(withTasks.get(0).path("taskStatus").asText()).isEqualTo("TODO");

        assertThat(listBetween(day(250), day(251)))
                .as("includeTasks=false thì lịch chỉ còn event")
                .isEmpty();
    }

    @Test
    @DisplayName("FR-CAL-05 — event liên kết được với một task")
    void anEventCanBeLinkedToATask() throws Exception {
        String taskId = data(mockMvc.perform(authed(post("/api/v1/tasks"))
                                .content(json(Map.of("title", "Chuẩn bị slide"))))
                        .andExpect(status().isCreated())
                        .andReturn())
                .path("id")
                .asText();

        String eventId = createEvent(Map.of(
                "title", "Thuyết trình nội bộ",
                "startAt", at(260, 10, 0).toString(),
                "endAt", at(260, 11, 0).toString(),
                "taskId", taskId));

        mockMvc.perform(authed(get("/api/v1/events/" + eventId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkedTaskId").value(taskId))
                .andExpect(jsonPath("$.data.linkedTaskTitle").value("Chuẩn bị slide"));

        assertThat(listBetween(day(260), day(261)))
                .singleElement()
                .satisfies(item ->
                        assertThat(item.path("linkedTaskId").asText()).isEqualTo(taskId));
    }

    @Test
    @DisplayName("Dữ liệu sai bị từ chối với mã lỗi có cấu trúc, không phải stack trace")
    void rejectsInvalidInputWithStructuredErrors() throws Exception {
        mockMvc.perform(authed(post("/api/v1/events"))
                        .content(json(Map.of(
                                "title", "Ngược thời gian",
                                "startAt", at(270, 10, 0).toString(),
                                "endAt", at(270, 9, 0).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("endAt"));

        mockMvc.perform(authed(post("/api/v1/events"))
                        .content(json(Map.of(
                                "title", "Lặp sai cú pháp",
                                "startAt", at(270, 9, 0).toString(),
                                "endAt", at(270, 10, 0).toString(),
                                "rrule", "KHONG-PHAI-RRULE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("rrule"));

        mockMvc.perform(authed(post("/api/v1/events"))
                        .content(json(Map.of(
                                "title", "Nhắc sai khoảng",
                                "startAt", at(270, 9, 0).toString(),
                                "endAt", at(270, 10, 0).toString(),
                                "reminderOffsets", List.of(7)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("reminderOffsets"));

        mockMvc.perform(authed(post("/api/v1/events"))
                        .content(json(Map.of(
                                "title", "",
                                "startAt", at(270, 9, 0).toString(),
                                "endAt", at(270, 10, 0).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("title"));

        mockMvc.perform(authed(get("/api/v1/events")
                        .param("from", at(270, 9, 0).toString())
                        .param("to", at(270, 9, 0).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("to"));

        mockMvc.perform(authed(get("/api/v1/events")
                        .param("from", day(0).toString())
                        .param("to", day(500).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("to"));
    }

    @Test
    @DisplayName("Mốc instance sai định dạng báo lỗi đúng trường thay vì lỗi 500")
    void rejectsAMalformedOccurrenceAnchor() throws Exception {
        String id = createEvent(weeklySeries(280));

        mockMvc.perform(authed(delete(URI.create(
                        "/api/v1/events/" + id + "/occurrences/khong-phai-thoi-gian"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("occurrenceStart"));
    }

    @Test
    @DisplayName("Gọi API lịch thiếu token trả 401")
    void requiresTheSharedToken() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .param("from", day(0).toString())
                        .param("to", day(30).toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
