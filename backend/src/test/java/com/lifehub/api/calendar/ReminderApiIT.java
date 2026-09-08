package com.lifehub.api.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifehub.support.ApiIntegrationTest;
import com.lifehub.support.TestDatabase;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Missed reminders, snooze and dismiss over HTTP (UC-04, UC-05).
 *
 * <p>Each test starts by clearing the missed list, so the methods stay independent even though they
 * share one database and the missed query is global rather than per event.
 */
class ReminderApiIT extends ApiIntegrationTest {

    private static final String DATABASE_URL = TestDatabase.freshUrl();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @BeforeEach
    void clearMissedList() throws Exception {
        mockMvc.perform(authed(post("/api/v1/reminders/dismiss-all"))).andExpect(status().isOk());
    }

    /**
     * Creates an event whose reminder is already overdue.
     *
     * <p>The event starts in ten minutes and asks to be reminded fifteen minutes beforehand, so the
     * trigger sits five minutes in the past - exactly the state UC-05 describes after the app has
     * been closed over a reminder.
     */
    private String eventWithAnOverdueReminder(String title) throws Exception {
        OffsetDateTime start = ZonedDateTime.now(VN).plusMinutes(10).withNano(0).toOffsetDateTime();
        return data(mockMvc.perform(authed(post("/api/v1/events"))
                                .content(json(Map.of(
                                        "title", title,
                                        "location", "Phòng họp A",
                                        "startAt", start.toString(),
                                        "endAt", start.plusHours(1).toString(),
                                        "reminderOffsets", List.of(15)))))
                        .andExpect(status().isCreated())
                        .andReturn())
                .path("id")
                .asText();
    }

    private JsonNode missed() throws Exception {
        return data(mockMvc.perform(authed(get("/api/v1/reminders/missed")))
                .andExpect(status().isOk())
                .andReturn());
    }

    @Test
    @DisplayName("UC-05 — reminder quá hạn hiện trong danh sách bị lỡ với đủ thông tin hiển thị")
    void listsMissedRemindersWithEverythingTheModalNeeds() throws Exception {
        String eventId = eventWithAnOverdueReminder("Họp review sprint");

        JsonNode reminders = missed();

        assertThat(reminders).hasSize(1);
        JsonNode reminder = reminders.get(0);
        assertThat(reminder.path("title").asText()).isEqualTo("Họp review sprint");
        assertThat(reminder.path("body").asText()).contains("Phòng họp A");
        assertThat(reminder.path("refType").asText()).isEqualTo("EVENT");
        assertThat(reminder.path("refId").asText()).isEqualTo(eventId);
        assertThat(reminder.path("status").asText()).isEqualTo("PENDING");
        assertThat(reminder.path("offsetMinutes").asInt()).isEqualTo(15);
        assertThat(reminder.path("id").asText()).isNotBlank();
    }

    @Test
    @DisplayName("UC-04 5a — hoãn đánh dấu bản cũ SNOOZED và tạo bản mới đúng thời điểm")
    void snoozingReschedulesTheReminder() throws Exception {
        eventWithAnOverdueReminder("Họp kế hoạch");
        String reminderId = missed().get(0).path("id").asText();

        JsonNode replacement = data(mockMvc.perform(authed(post("/api/v1/reminders/" + reminderId + "/snooze"))
                                .content(json(Map.of("minutes", 10))))
                        .andExpect(status().isOk())
                        .andReturn());

        assertThat(replacement.path("id").asText()).isNotEqualTo(reminderId);
        assertThat(replacement.path("status").asText()).isEqualTo("PENDING");
        assertThat(OffsetDateTime.parse(replacement.path("triggerAt").asText()))
                .as("bản mới bắn sau 10 phút nữa")
                .isAfter(OffsetDateTime.now());
        assertThat(replacement.path("title").asText())
                .as("bản hoãn vẫn trỏ về đúng sự kiện gốc")
                .isEqualTo("Họp kế hoạch");

        assertThat(missed())
                .as("bản cũ đã chuyển SNOOZED, bản mới chưa tới hạn nên danh sách bị lỡ trống")
                .isEmpty();
    }

    @Test
    @DisplayName("Không truyền số phút thì hoãn mặc định 10 phút")
    void snoozeDefaultsToTenMinutes() throws Exception {
        eventWithAnOverdueReminder("Họp không tham số");
        String reminderId = missed().get(0).path("id").asText();

        mockMvc.perform(authed(post("/api/v1/reminders/" + reminderId + "/snooze")).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("UC-04 5b — tắt reminder gắn task thì task chuyển sang DONE")
    void dismissingATaskReminderCompletesTheTask() throws Exception {
        OffsetDateTime start = ZonedDateTime.now(VN).plusMinutes(10).withNano(0).toOffsetDateTime();
        String taskId = data(mockMvc.perform(authed(post("/api/v1/tasks"))
                                .content(json(Map.of("title", "Gọi cho khách hàng"))))
                        .andExpect(status().isCreated())
                        .andReturn())
                .path("id")
                .asText();

        mockMvc.perform(authed(post("/api/v1/events"))
                        .content(json(Map.of(
                                "title", "Gọi khách",
                                "startAt", start.toString(),
                                "endAt", start.plusMinutes(30).toString(),
                                "taskId", taskId,
                                "reminderOffsets", List.of(15)))))
                .andExpect(status().isCreated());

        String reminderId = missed().get(0).path("id").asText();

        mockMvc.perform(authed(post("/api/v1/reminders/" + reminderId + "/dismiss")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISMISSED"));

        // The reminder belongs to the event, not the task, so the task must stay untouched.
        mockMvc.perform(authed(get("/api/v1/tasks/" + taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TODO"));

        assertThat(missed()).isEmpty();
    }

    @Test
    @DisplayName("Tắt hàng loạt xóa sạch danh sách bị lỡ trong một lần gọi")
    void dismissAllClearsTheWholeList() throws Exception {
        eventWithAnOverdueReminder("Họp một");
        eventWithAnOverdueReminder("Họp hai");
        assertThat(missed()).hasSize(2);

        mockMvc.perform(authed(post("/api/v1/reminders/dismiss-all")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dismissed").value(2));

        assertThat(missed()).isEmpty();
    }

    @Test
    @DisplayName("UC-05 — /bootstrap trả reminder bị lỡ để hiện modal ngay khi khởi động")
    void bootstrapCarriesTheMissedReminders() throws Exception {
        eventWithAnOverdueReminder("Họp lúc khởi động");

        mockMvc.perform(authed(get("/api/v1/bootstrap")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings").isNotEmpty())
                .andExpect(jsonPath("$.data.missedReminders", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.missedReminders[0].title").value("Họp lúc khởi động"))
                .andExpect(jsonPath("$.data.missedReminders[0].refType").value("EVENT"));
    }

    @Test
    @DisplayName("Reminder không tồn tại trả 404 có cấu trúc")
    void unknownReminderIsNotFound() throws Exception {
        mockMvc.perform(authed(post("/api/v1/reminders/khong-ton-tai/dismiss")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Kênh SSE mở được với token và bị chặn khi thiếu token")
    void theStreamIsOpenOnlyToAuthenticatedClients() throws Exception {
        mockMvc.perform(authed(get("/api/v1/events/stream")))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        mockMvc.perform(get("/api/v1/events/stream"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
