package com.lifehub.api.task;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifehub.support.ApiIntegrationTest;
import com.lifehub.support.TestDatabase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Project and tag endpoints over HTTP (T1-10, T1-11). */
class ProjectAndTagApiIT extends ApiIntegrationTest {

    private static final String DATABASE_URL = TestDatabase.freshUrl();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Test
    @DisplayName("T1-10 — xóa project thì task bên trong còn nguyên, project_id thành null")
    void deletingAProjectReleasesItsTasksInsteadOfDestroyingThem() throws Exception {
        String projectId = createProject("Dự án sẽ bị xóa");
        String taskId = createTask(Map.of("title", "Task thuộc dự án", "projectId", projectId));

        mockMvc.perform(authed(get("/api/v1/tasks/" + taskId)))
                .andExpect(jsonPath("$.data.project.id").value(projectId));

        mockMvc.perform(authed(delete("/api/v1/projects/" + projectId))).andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/v1/tasks/" + taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Task thuộc dự án"))
                .andExpect(jsonPath("$.data.project").doesNotExist());

        mockMvc.perform(authed(get("/api/v1/projects/" + projectId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-PRJ-03 — danh sách project kèm số task và phần trăm hoàn thành")
    void reportsProgressPerProject() throws Exception {
        String projectId = createProject("Dự án có tiến độ");
        String first = createTask(Map.of("title", "Task 1", "projectId", projectId));
        createTask(Map.of("title", "Task 2", "projectId", projectId));
        createTask(Map.of("title", "Task 3", "projectId", projectId));
        createTask(Map.of("title", "Task 4", "projectId", projectId));

        mockMvc.perform(authed(patch("/api/v1/tasks/" + first + "/status"))
                        .content(json(Map.of("status", "DONE"))))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/v1/projects/" + projectId)))
                .andExpect(jsonPath("$.data.taskCount").value(4))
                .andExpect(jsonPath("$.data.completedTaskCount").value(1))
                .andExpect(jsonPath("$.data.progressPercent").value(25));
    }

    @Test
    @DisplayName("Project chưa có task nào thì tiến độ là 0%, không chia cho 0")
    void reportsZeroProgressForAnEmptyProject() throws Exception {
        String projectId = createProject("Dự án rỗng");

        mockMvc.perform(authed(get("/api/v1/projects/" + projectId)))
                .andExpect(jsonPath("$.data.taskCount").value(0))
                .andExpect(jsonPath("$.data.progressPercent").value(0));
    }

    @Test
    @DisplayName("Trùng tên project trả 409 CONFLICT")
    void rejectsADuplicateProjectName() throws Exception {
        createProject("Dự án trùng tên");

        mockMvc.perform(authed(post("/api/v1/projects"))
                        .content(json(Map.of("name", "Dự án trùng tên"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.field").value("name"));
    }

    @Test
    @DisplayName("Lưu trữ project bằng PATCH status=ARCHIVED")
    void archivesAProject() throws Exception {
        String projectId = createProject("Dự án sẽ lưu trữ");

        mockMvc.perform(authed(patch("/api/v1/projects/" + projectId))
                        .content(json(Map.of("status", "ARCHIVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(authed(get("/api/v1/projects").param("includeArchived", "false")))
                .andExpect(jsonPath("$[?(@.name == 'Dự án sẽ lưu trữ')]").doesNotExist());
    }

    @Test
    @DisplayName("T1-11 — tạo tag trùng tên trả 409 CONFLICT")
    void rejectsADuplicateTagName() throws Exception {
        mockMvc.perform(authed(post("/api/v1/tags")).content(json(Map.of("name", "docs"))))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(post("/api/v1/tags")).content(json(Map.of("name", "docs"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.field").value("name"));
    }

    @Test
    @DisplayName("T1-11 — trùng tên không phân biệt hoa thường")
    void treatsTagNamesCaseInsensitively() throws Exception {
        mockMvc.perform(authed(post("/api/v1/tags")).content(json(Map.of("name", "khẩn cấp"))))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(post("/api/v1/tags")).content(json(Map.of("name", "KHẨN CẤP"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Danh sách tag kèm số lần được dùng")
    void reportsTagUsage() throws Exception {
        String tagId = createTag("thường-dùng");
        createTask(Map.of("title", "Task A", "tagIds", List.of(tagId)));
        createTask(Map.of("title", "Task B", "tagIds", List.of(tagId)));

        mockMvc.perform(authed(get("/api/v1/tags")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == 'thường-dùng')].usageCount").value(2));
    }

    @Test
    @DisplayName("FR-TSK-06 — xóa tag gỡ nó khỏi mọi task, task vẫn còn")
    void deletingATagDetachesItFromTasks() throws Exception {
        String tagId = createTag("sẽ-bị-xóa");
        String taskId = createTask(Map.of("title", "Task có nhãn", "tagIds", List.of(tagId)));

        mockMvc.perform(authed(get("/api/v1/tasks/" + taskId)))
                .andExpect(jsonPath("$.data.tags.length()").value(1));

        mockMvc.perform(authed(delete("/api/v1/tags/" + tagId))).andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/v1/tasks/" + taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Task có nhãn"))
                .andExpect(jsonPath("$.data.tags.length()").value(0));
    }

    @Test
    @DisplayName("Tên tag rỗng trả 400")
    void rejectsAnEmptyTagName() throws Exception {
        mockMvc.perform(authed(post("/api/v1/tags")).content(json(Map.of("name", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private String createProject(String name) throws Exception {
        var result = mockMvc.perform(authed(post("/api/v1/projects")).content(json(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return data(result).get("id").asText();
    }

    private String createTag(String name) throws Exception {
        var result = mockMvc.perform(authed(post("/api/v1/tags")).content(json(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return data(result).get("id").asText();
    }

    private String createTask(Map<String, Object> body) throws Exception {
        var result = mockMvc.perform(authed(post("/api/v1/tasks")).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return data(result).get("id").asText();
    }
}
