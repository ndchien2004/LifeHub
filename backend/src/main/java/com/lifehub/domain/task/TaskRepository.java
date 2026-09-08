package com.lifehub.domain.task;

import com.lifehub.domain.common.Page;
import com.lifehub.domain.common.PageRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port for tasks. Implemented in {@code infrastructure.persistence}. */
public interface TaskRepository {

    Task save(Task task);

    /** Looks up a live task; soft deleted rows are invisible here. */
    Optional<Task> findById(String id);

    /** Looks up a task even when soft deleted - needed by restore (FR-TSK-03). */
    Optional<Task> findByIdIncludingDeleted(String id);

    List<Task> findAllById(List<String> ids);

    /** Direct children of a task, ordered by sort order. Only ever one level deep (FR-TSK-11). */
    List<Task> findSubtasks(String parentId);

    Page<Task> search(TaskFilter filter, PageRequest pageRequest);

    /** Detaches every task from a project being deleted, leaving the tasks intact (FR-PRJ-02). */
    int clearProject(String projectId);

    /** Live task counts for a project: total and completed, for the progress bar (FR-PRJ-03). */
    Counts countByProject(String projectId);

    /**
     * Subtask counts for many parents in one query.
     *
     * <p>Batched deliberately: a list of 1.000 tasks asking for its own subtask count one row at
     * a time is the classic N+1 that would blow the 500 ms budget in T1-12.
     */
    java.util.Map<String, Counts> countSubtasks(List<String> parentIds);

    /** Hard deletes tasks soft deleted before the cutoff, ending the 30 day window (FR-TSK-03). */
    int purgeDeletedBefore(Instant cutoff);

    /** How many live tasks carry each of the given tags, keyed by tag id. */
    java.util.Map<String, Long> countUsageByTag();

    /** A total and how many of them are DONE. */
    record Counts(long total, long completed) {
        public static final Counts EMPTY = new Counts(0, 0);

        public int percent() {
            return total == 0 ? 0 : (int) Math.round(100.0 * completed / total);
        }
    }
}
