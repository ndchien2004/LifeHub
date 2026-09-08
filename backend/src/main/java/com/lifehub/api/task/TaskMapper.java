package com.lifehub.api.task;

import com.lifehub.api.task.TaskDtos.ProjectRef;
import com.lifehub.api.task.TaskDtos.ProjectResponse;
import com.lifehub.api.task.TaskDtos.TagResponse;
import com.lifehub.api.task.TaskDtos.TaskResponse;
import com.lifehub.domain.task.Project;
import com.lifehub.domain.task.Tag;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskRepository.Counts;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Converts domain entities into API responses.
 *
 * <p>Two conversions matter here. Instants become {@link OffsetDateTime} in the display zone, so
 * the frontend gets {@code 2026-09-10T17:00:00+07:00} instead of a bare UTC Z. And {@code isOverdue}
 * is computed against the injected clock rather than stored, because it changes with the passage of
 * time rather than with any edit (FR-TSK-12).
 */
@Component
public class TaskMapper {

    private final Clock clock;
    private final ZoneId displayZone;

    public TaskMapper(Clock clock, ZoneId displayZone) {
        this.clock = clock;
        this.displayZone = displayZone;
    }

    public TaskResponse toResponse(Task task, Counts subtaskCounts, List<Task> subtasks) {
        Counts counts = subtaskCounts == null ? Counts.EMPTY : subtaskCounts;
        Instant now = clock.instant();

        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                toOffset(task.getDueAt()),
                task.isOverdue(now),
                toRef(task.getProject()),
                task.getTags().stream()
                        .sorted(Comparator.comparing(Tag::getName))
                        .map(this::toResponse)
                        .toList(),
                task.getParent() == null ? null : task.getParent().getId(),
                (int) counts.total(),
                (int) counts.completed(),
                subtasks == null ? null : subtasks.stream().map(this::toSummary).toList(),
                task.getEstimateMinutes(),
                task.getRrule(),
                task.getSortOrder(),
                toOffset(task.getCompletedAt()),
                toOffset(task.getDeletedAt()),
                toOffset(task.getCreatedAt()),
                toOffset(task.getUpdatedAt()));
    }

    /** A task rendered without its own subtasks - used for the nested children of a parent. */
    public TaskResponse toSummary(Task task) {
        return toResponse(task, Counts.EMPTY, null);
    }

    public List<TaskResponse> toResponses(List<Task> tasks, Map<String, Counts> subtaskCounts) {
        return tasks.stream()
                .map(task -> toResponse(task, subtaskCounts.get(task.getId()), null))
                .toList();
    }

    public TagResponse toResponse(Tag tag) {
        return toResponse(tag, 0);
    }

    public TagResponse toResponse(Tag tag, long usageCount) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getColor(), usageCount);
    }

    public ProjectResponse toResponse(Project project, Counts counts) {
        Counts resolved = counts == null ? Counts.EMPTY : counts;
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getColor(),
                project.getDescription(),
                project.getStatus(),
                resolved.total(),
                resolved.completed(),
                resolved.percent(),
                toOffset(project.getCreatedAt()));
    }

    public ProjectRef toRef(Project project) {
        return project == null ? null : new ProjectRef(project.getId(), project.getName(), project.getColor());
    }

    public OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atZone(displayZone).toOffsetDateTime();
    }

    public Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
