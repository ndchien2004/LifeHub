package com.lifehub.domain.task;

import java.time.Instant;
import java.util.List;

/**
 * The filter criteria behind {@code GET /tasks} (FR-TSK-08, FR-TSK-10).
 *
 * <p>A null or empty member means "do not constrain on this". Criteria combine with AND, so
 * several filters applied together narrow the result rather than widening it.
 *
 * @param projectId single project, or null for any
 * @param tagIds task must carry at least one of these tags
 * @param statuses any of these statuses
 * @param priorities any of these priorities
 * @param dueFrom inclusive lower bound on {@code due_at}
 * @param dueTo inclusive upper bound on {@code due_at}
 * @param keyword case insensitive substring of title or description
 * @param includeDeleted whether soft deleted tasks are returned as well
 * @param topLevelOnly whether to exclude subtasks, which the list and Kanban views nest instead
 */
public record TaskFilter(
        String projectId,
        List<String> tagIds,
        List<TaskStatus> statuses,
        List<Priority> priorities,
        Instant dueFrom,
        Instant dueTo,
        String keyword,
        boolean includeDeleted,
        boolean topLevelOnly) {

    public static TaskFilter none() {
        return new TaskFilter(null, null, null, null, null, null, null, false, false);
    }

    public boolean hasTagFilter() {
        return tagIds != null && !tagIds.isEmpty();
    }
}
