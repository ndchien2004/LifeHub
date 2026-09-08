package com.lifehub.api.task;

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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Full task lifecycle over HTTP (T1-08, T1-09). */
class TaskApiIT extends ApiIntegrationTest {

    private static final String DATABASE_URL = TestDatabase.freshUrl();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Test
    @DisplayName("T1-08 — CRUD đầy đủ: tạo, đọc, sửa, đổi trạng thái, xóa")
    void supportsTheFullLifecycle() throws Exception {
        // Create
        String id = createTask(Map.of(
                "title", "Hoàn thiện SRS",
                "description", "Viết đủ use case và ERD",
                "priority", "HIGH",
                "estimateMinutes", 180));

        // Read
        mockMvc.perform(authed(get("/api/v1/tasks/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Hoàn thiện SRS"))
                .andExpect(jsonPath("$.data.status").value("TODO"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.estimateMinutes").value(180))
                .andExpect(jsonPath("$.data.isOverdue").value(false))
                .andExpect(jsonPath("$.data.subtaskCount").value(0));

        // Update
        mockMvc.perform(authed(patch("/api/v1/tasks/" + id))
                        .content(json(Map.of("title", "Hoàn thiện SRS v2", "priority", "URGENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Hoàn thiện SRS v2"))
                .andExpect(jsonPath("$.data.priority").value("URGENT"))
                .andExpect(jsonPath("$.data.estimateMinutes").value(180));

        // Status change
        mockMvc.perform(authed(patch("/api/v1/tasks/" + id + "/status"))
                        .content(json(Map.of("status", "DONE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        // Delete
        mockMvc.perform(authed(delete("/api/v1/tasks/" + id))).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/tasks/" + id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("T1-09 — xóa mềm rồi khôi phục thì task quay lại danh sách")
    void restoresASoftDeletedTask() throws Exception {
        String id = createTask(Map.of("title", "Task sẽ bị xóa"));

        mockMvc.perform(authed(delete("/api/v1/tasks/" + id))).andExpect(status().isOk());
        assertThat(listTitles()).doesNotContain("Task sẽ bị xóa");

        mockMvc.perform(authed(post("/api/v1/tasks/" + id + "/restore")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedAt").doesNotExist());

        assertThat(listTitles()).contains("Task sẽ bị xóa");
    }

    @Test
    @DisplayName("Task đã xóa vẫn tìm được khi includeDeleted=true — nguồn cho nút Hoàn tác")
    void exposesDeletedTasksOnDemand() throws Exception {
        String id = createTask(Map.of("title", "Task trong thùng rác"));
        mockMvc.perform(authed(delete("/api/v1/tasks/" + id))).andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/v1/tasks").param("includeDeleted", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.title == 'Task trong thùng rác')]").exists());
    }

    @Test
    @DisplayName("T1-01 — POST tiêu đề rỗng trả 400 VALIDATION_ERROR kèm tên trường")
    void rejectsAnEmptyTitle() throws Exception {
        mockMvc.perform(authed(post("/api/v1/tasks")).content(json(Map.of("title", "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("title"));
    }

    @Test
    @DisplayName("T1-04 — POST subtask của subtask trả 400, không tạo bản ghi nào")
    void rejectsNestingBeyondOneLevel() throws Exception {
        String parentId = createTask(Map.of("title", "Chuẩn bị release"));
        String childId = createTask(Map.of("title", "Viết changelog", "parentId", parentId));

        mockMvc.perform(authed(post("/api/v1/tasks"))
                        .content(json(Map.of("title", "Rà soát chính tả", "parentId", childId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("parentId"));

        assertThat(listTitles()).doesNotContain("Rà soát chính tả");
    }

    @Test
    @DisplayName("Chi tiết task cha kèm subtask và số đếm đã hoàn thành")
    void returnsSubtasksWithTheParent() throws Exception {
        String parentId = createTask(Map.of("title", "Chuẩn bị release"));
        String firstChild = createTask(Map.of("title", "Viết changelog", "parentId", parentId));
        createTask(Map.of("title", "Tag phiên bản", "parentId", parentId));

        mockMvc.perform(authed(patch("/api/v1/tasks/" + firstChild + "/status"))
                        .content(json(Map.of("status", "DONE"))))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/v1/tasks/" + parentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtaskCount").value(2))
                .andExpect(jsonPath("$.data.completedSubtaskCount").value(1))
                .andExpect(jsonPath("$.data.subtasks.length()").value(2));
    }

    @Test
    @DisplayName("FR-TSK-12 — task quá hạn chưa xong thì isOverdue=true, xong rồi thì false")
    void reportsOverdueState() throws Exception {
        String id = createTask(Map.of(
                "title", "Nộp báo cáo",
                "dueAt", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString()));

        mockMvc.perform(authed(get("/api/v1/tasks/" + id)))
                .andExpect(jsonPath("$.data.isOverdue").value(true));

        mockMvc.perform(authed(patch("/api/v1/tasks/" + id + "/status"))
                        .content(json(Map.of("status", "DONE"))))
                .andExpect(jsonPath("$.data.isOverdue").value(false));
    }

    @Test
    @DisplayName("PATCH gửi dueAt: null thì xóa hạn chót, không đụng các trường khác")
    void clearsTheDueDateWhenSentAsNull() throws Exception {
        String id = createTask(Map.of(
                "title", "Nộp báo cáo",
                "dueAt", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString()));

        String body = "{\"dueAt\": null}";
        mockMvc.perform(authed(patch("/api/v1/tasks/" + id)).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dueAt").doesNotExist())
                .andExpect(jsonPath("$.data.title").value("Nộp báo cáo"));
    }

    @Test
    @DisplayName("Thời gian trả về theo múi giờ hiển thị, không phải Z thô")
    void rendersTimestampsWithTheDisplayOffset() throws Exception {
        String id = createTask(Map.of("title", "Kiểm tra múi giờ"));

        JsonNode task = data(mockMvc.perform(authed(get("/api/v1/tasks/" + id))).andReturn());

        assertThat(task.get("createdAt").asText())
                .as("06-API-SPEC.md quy định ISO-8601 có offset")
                .endsWith("+07:00");
    }

    @Test
    @DisplayName("Đổi trạng thái thiếu status trả 400 thay vì 500")
    void rejectsAStatusChangeWithoutAStatus() throws Exception {
        String id = createTask(Map.of("title", "Task nào đó"));

        mockMvc.perform(authed(patch("/api/v1/tasks/" + id + "/status")).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Reorder hàng loạt lưu đúng sort_order")
    void appliesABulkReorder() throws Exception {
        String first = createTask(Map.of("title", "A"));
        String second = createTask(Map.of("title", "B"));

        mockMvc.perform(authed(patch("/api/v1/tasks/reorder"))
                        .content(json(Map.of("items", java.util.List.of(
                                Map.of("taskId", first, "sortOrder", 10),
                                Map.of("taskId", second, "sortOrder", 20))))))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/v1/tasks/" + first)))
                .andExpect(jsonPath("$.data.sortOrder").value(10));
        mockMvc.perform(authed(get("/api/v1/tasks/" + second)))
                .andExpect(jsonPath("$.data.sortOrder").value(20));
    }

    @Test
    @DisplayName("Giá trị enum sai trong query trả 400 có thông báo đọc được")
    void rejectsAnUnknownEnumValue() throws Exception {
        mockMvc.perform(authed(get("/api/v1/tasks").param("status", "KHONG_CO_THAT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("status"));
    }

    @Test
    @DisplayName("Body JSON hỏng trả 400 chứ không phải 500 — lỗi của client, không phải của server")
    void reportsAMalformedBodyAsAClientError() throws Exception {
        mockMvc.perform(authed(post("/api/v1/tasks")).content("{\"title\": \"thiếu ngoặc\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Giá trị enum sai trong body cũng trả 400, kèm thông báo tiếng Việt")
    void reportsAnUnknownEnumInTheBodyAsAClientError() throws Exception {
        mockMvc.perform(authed(post("/api/v1/tasks"))
                        .content("{\"title\": \"Task\", \"priority\": \"SIEU_GAP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("không đọc được")));
    }

    @Test
    @DisplayName("Gán task vào project không tồn tại trả 404")
    void rejectsAnUnknownProject() throws Exception {
        mockMvc.perform(authed(post("/api/v1/tasks"))
                        .content(json(Map.of("title", "Task", "projectId", "khong-ton-tai"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    private String createTask(Map<String, Object> body) throws Exception {
        var result = mockMvc.perform(authed(post("/api/v1/tasks")).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return data(result).get("id").asText();
    }

    private String listTitles() throws Exception {
        return body(mockMvc.perform(authed(get("/api/v1/tasks").param("size", "200"))).andReturn());
    }
}
