package com.lifehub.api.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifehub.domain.task.Priority;
import com.lifehub.domain.task.ProjectStatus;
import com.lifehub.domain.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request and response shapes for the task module (06-API-SPEC.md §3-4).
 *
 * <p>Timestamps cross the wire as {@link OffsetDateTime} in the machine local zone, which is the
 * ISO-8601-with-offset form the spec shows. They are stored as UTC instants; the mapper converts.
 */
public final class TaskDtos {

    private TaskDtos() {
    }

    // ---------- responses ----------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TaskResponse(
            String id,
            String title,
            String description,
            TaskStatus status,
            Priority priority,
            OffsetDateTime dueAt,
            boolean isOverdue,
            ProjectRef project,
            List<TagResponse> tags,
            String parentId,
            int subtaskCount,
            int completedSubtaskCount,
            List<TaskResponse> subtasks,
            Integer estimateMinutes,
            int sortOrder,
            OffsetDateTime completedAt,
            OffsetDateTime deletedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record ProjectRef(String id, String name, String color) {
    }

    public record TagResponse(String id, String name, String color, long usageCount) {
    }

    public record ProjectResponse(
            String id,
            String name,
            String color,
            String description,
            ProjectStatus status,
            long taskCount,
            long completedTaskCount,
            int progressPercent,
            OffsetDateTime createdAt) {
    }

    // ---------- requests ----------

    public record CreateTaskRequest(
            @NotBlank(message = "Tiêu đề không được để trống")
            @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
            String title,
            String description,
            Priority priority,
            OffsetDateTime dueAt,
            String projectId,
            String parentId,
            List<String> tagIds,
            @Positive(message = "Ước lượng thời gian phải lớn hơn 0")
            Integer estimateMinutes) {
    }

    /**
     * PATCH body. Every member is optional, and a member explicitly sent as null clears the
     * field - which is why the controller reads the raw JSON node rather than trusting nulls here.
     */
    public record UpdateTaskRequest(
            @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự") String title,
            String description,
            Priority priority,
            TaskStatus status,
            OffsetDateTime dueAt,
            String projectId,
            List<String> tagIds,
            @Positive(message = "Ước lượng thời gian phải lớn hơn 0") Integer estimateMinutes) {
    }

    public record ChangeStatusRequest(TaskStatus status) {
    }

    public record ReorderRequest(List<ReorderItem> items) {
    }

    public record ReorderItem(@NotBlank String taskId, int sortOrder) {
    }

    public record CreateProjectRequest(
            @NotBlank(message = "Tên dự án không được để trống")
            @Size(max = 100, message = "Tên dự án tối đa 100 ký tự")
            String name,
            String color,
            String description,
            ProjectStatus status) {
    }

    public record UpdateProjectRequest(
            @Size(max = 100, message = "Tên dự án tối đa 100 ký tự") String name,
            String color,
            String description,
            ProjectStatus status) {
    }

    public record CreateTagRequest(
            @NotBlank(message = "Tên nhãn không được để trống")
            @Size(max = 50, message = "Tên nhãn tối đa 50 ký tự")
            String name,
            String color) {
    }

    public record UpdateTagRequest(
            @Size(max = 50, message = "Tên nhãn tối đa 50 ký tự") String name, String color) {
    }
}
