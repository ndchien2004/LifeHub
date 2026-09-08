package com.lifehub.application.task;

import com.lifehub.domain.task.Priority;
import com.lifehub.domain.task.ProjectStatus;
import com.lifehub.domain.task.TaskStatus;
import java.time.Instant;
import java.util.List;

/**
 * Input records for the write side.
 *
 * <p>Update commands wrap each field in an {@link Optional} style holder rather than using null,
 * because PATCH has to distinguish "leave this alone" from "set this to null" - clearing a due
 * date and not touching it are different intentions that a bare null cannot express.
 */
public final class TaskCommands {

    private TaskCommands() {
    }

    /** A field that may be absent (leave unchanged) or present with a possibly null value. */
    public record Patch<T>(boolean present, T value) {

        private static final Patch<?> ABSENT = new Patch<>(false, null);

        @SuppressWarnings("unchecked")
        public static <T> Patch<T> absent() {
            return (Patch<T>) ABSENT;
        }

        public static <T> Patch<T> of(T value) {
            return new Patch<>(true, value);
        }

        public void ifPresent(java.util.function.Consumer<T> action) {
            if (present) {
                action.accept(value);
            }
        }
    }

    public record CreateTask(
            String title,
            String description,
            Priority priority,
            Instant dueAt,
            String projectId,
            String parentId,
            List<String> tagIds,
            Integer estimateMinutes) {
    }

    public record UpdateTask(
            Patch<String> title,
            Patch<String> description,
            Patch<Priority> priority,
            Patch<TaskStatus> status,
            Patch<Instant> dueAt,
            Patch<String> projectId,
            Patch<List<String>> tagIds,
            Patch<Integer> estimateMinutes) {
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
