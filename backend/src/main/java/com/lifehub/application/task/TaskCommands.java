package com.lifehub.application.task;

import com.lifehub.domain.common.Patch;
import com.lifehub.domain.task.Priority;
import com.lifehub.domain.task.ProjectStatus;
import com.lifehub.domain.task.TaskStatus;
import java.time.Instant;
import java.util.List;

/**
 * Input records for the write side.
 *
 * <p>Update commands wrap each field in a {@link Patch} rather than using null, because PATCH has
 * to distinguish "leave this alone" from "set this to null".
 */
public final class TaskCommands {

    private TaskCommands() {
    }

    public record CreateTask(
            String title,
            String description,
            Priority priority,
            Instant dueAt,
            String projectId,
            String parentId,
            List<String> tagIds,
            Integer estimateMinutes,
            String rrule) {
    }

    public record UpdateTask(
            Patch<String> title,
            Patch<String> description,
            Patch<Priority> priority,
            Patch<TaskStatus> status,
            Patch<Instant> dueAt,
            Patch<String> projectId,
            Patch<List<String>> tagIds,
            Patch<Integer> estimateMinutes,
            Patch<String> rrule) {
    }

    public record CreateProject(String name, String color, String description, ProjectStatus status) {
    }

    public record UpdateProject(
            Patch<String> name,
            Patch<String> color,
            Patch<String> description,
            Patch<ProjectStatus> status) {
    }

    public record ReorderEntry(String taskId, int sortOrder) {
    }
}
