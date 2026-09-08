package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.common.Page;
import com.lifehub.domain.common.PageRequest;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskFilter;
import com.lifehub.domain.task.TaskRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the {@link TaskRepository} port. */
@Repository
public class TaskRepositoryAdapter implements TaskRepository {

    /** Allow list: sorting on an arbitrary caller supplied field would expose the entity graph. */
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("dueAt", "priority", "createdAt", "updatedAt", "title", "sortOrder", "status");

    private static final String DEFAULT_SORT_FIELD = "createdAt";

    private final SpringDataTaskRepository delegate;

    public TaskRepositoryAdapter(SpringDataTaskRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Task save(Task task) {
        return delegate.save(task);
    }

    @Override
    public Optional<Task> findById(String id) {
        return delegate.findById(id).filter(task -> !task.isDeleted());
    }

    @Override
    public Optional<Task> findByIdIncludingDeleted(String id) {
        return delegate.findById(id);
    }

    @Override
    public List<Task> findAllById(List<String> ids) {
        return delegate.findAllById(ids);
    }

    @Override
    public List<Task> findSubtasks(String parentId) {
        return delegate.findSubtasks(parentId);
    }

    @Override
    public List<Task> findSubtasksIncludingDeleted(String parentId) {
        return delegate.findSubtasksIncludingDeleted(parentId);
    }

    @Override
    public Page<Task> search(TaskFilter filter, PageRequest pageRequest) {
        org.springframework.data.domain.PageRequest springPage =
                org.springframework.data.domain.PageRequest.of(
                        pageRequest.page(), pageRequest.size(), sortOf(pageRequest));

        org.springframework.data.domain.Page<Task> result =
                delegate.findAll(TaskSpecifications.from(filter), springPage);

        return new Page<>(
                result.getContent(), pageRequest.page(), pageRequest.size(), result.getTotalElements());
    }

    @Override
    public int clearProject(String projectId) {
        return delegate.clearProject(projectId);
    }

    @Override
    public Counts countByProject(String projectId) {
        return new Counts(
                delegate.countByProject(projectId), delegate.countCompletedByProject(projectId));
    }

    @Override
    public Map<String, Counts> countSubtasks(List<String> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Counts> counts = new HashMap<>();
        for (Object[] row : delegate.countSubtasks(parentIds)) {
            counts.put((String) row[0], new Counts(((Number) row[1]).longValue(), ((Number) row[2]).longValue()));
        }
        return counts;
    }

    @Override
    public int purgeDeletedBefore(Instant cutoff) {
        return delegate.purgeDeletedBefore(cutoff);
    }

    @Override
    public Map<String, Long> countUsageByTag() {
        Map<String, Long> usage = new HashMap<>();
        for (Object[] row : delegate.countUsageByTag()) {
            usage.put((String) row[0], ((Number) row[1]).longValue());
        }
        return usage;
    }

    private Sort sortOf(PageRequest pageRequest) {
        String field = pageRequest.sortField();
        String resolved =
                field != null && SORTABLE_FIELDS.contains(field) ? field : DEFAULT_SORT_FIELD;
        Sort primary =
                Sort.by(pageRequest.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, resolved);

        // Tie breaker: paging over rows with equal sort keys can otherwise repeat or skip rows.
        return resolved.equals(DEFAULT_SORT_FIELD)
                ? primary
                : primary.and(Sort.by(Sort.Direction.DESC, DEFAULT_SORT_FIELD));
    }
}
