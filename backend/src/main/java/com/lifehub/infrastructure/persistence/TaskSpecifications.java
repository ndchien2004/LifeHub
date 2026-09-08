package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.task.Tag;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskFilter;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates a {@link TaskFilter} into a JPA Specification (FR-TSK-08, FR-TSK-10).
 *
 * <p>Only the criteria the caller actually set contribute a predicate, so an empty filter costs
 * nothing and combined filters narrow the result with AND.
 */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> from(TaskFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!filter.includeDeleted()) {
                predicates.add(cb.isNull(root.get("deletedAt")));
            }
            if (filter.topLevelOnly()) {
                predicates.add(cb.isNull(root.get("parent")));
            }
            if (filter.projectId() != null && !filter.projectId().isBlank()) {
                predicates.add(cb.equal(root.get("project").get("id"), filter.projectId()));
            }
            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }
            if (filter.priorities() != null && !filter.priorities().isEmpty()) {
                predicates.add(root.get("priority").in(filter.priorities()));
            }
            if (filter.dueFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueAt"), filter.dueFrom()));
            }
            if (filter.dueTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueAt"), filter.dueTo()));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String pattern = "%" + filter.keyword().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern)));
            }
            if (filter.hasTagFilter()) {
                // A task carrying two of the requested tags would otherwise come back twice.
                if (query != null) {
                    query.distinct(true);
                }
                Join<Task, Tag> tags = root.join("tags", JoinType.INNER);
                predicates.add(tags.get("id").in(filter.tagIds()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
