package com.lifehub.domain.task;

import com.lifehub.domain.common.BaseEntity;
import com.lifehub.domain.common.DomainException;
import com.lifehub.domain.common.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** A unit of work (FR-TSK-01 → FR-TSK-12). */
@Entity
@Table(name = "task")
public class Task extends BaseEntity {

    public static final int MAX_TITLE_LENGTH = 255;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Task parent;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status = TaskStatus.TODO;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "estimate_minutes")
    private Integer estimateMinutes;

    /** Repetition rule; null means the task happens once (FR-TSK-13). */
    @Column(name = "rrule")
    private String rrule;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 200)
    @JoinTable(
            name = "task_tag",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    protected Task() {
    }

    public Task(String title) {
        retitle(title);
    }

    public void retitle(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("Tiêu đề không được để trống", "title");
        }
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new ValidationException(
                    "Tiêu đề tối đa " + MAX_TITLE_LENGTH + " ký tự", "title");
        }
        this.title = trimmed;
    }

    public void describe(String description) {
        this.description = description;
    }

    public void prioritise(Priority priority) {
        if (priority != null) {
            this.priority = priority;
        }
    }

    public void schedule(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public void estimate(Integer minutes) {
        if (minutes != null && minutes <= 0) {
            throw new ValidationException("Ước lượng thời gian phải lớn hơn 0", "estimateMinutes");
        }
        this.estimateMinutes = minutes;
    }

    public void moveTo(Project project) {
        this.project = project;
    }

    public void reorder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** Sets or clears the repetition rule (FR-TSK-13). Blank is stored as null. */
    public void repeat(String rrule) {
        this.rrule = rrule == null || rrule.isBlank() ? null : rrule.trim();
    }

    /**
     * Whether completing this task should produce a successor (FR-TSK-13).
     *
     * <p>A deadline is required: the rule says how often, and only the deadline says from when.
     * Subtasks are excluded because their successor would need a parent, and the one level rule of
     * FR-TSK-11 leaves nowhere sensible to put it.
     */
    public boolean isRepeating() {
        return rrule != null && dueAt != null && !isSubtask();
    }

    /**
     * A fresh TODO copy of this task, due at the next occurrence (FR-TSK-13).
     *
     * <p>Everything the user configured carries over; everything the previous run accumulated -
     * status, completion time, subtasks - does not. Tags are copied by reference, which is what
     * makes the new instance show up under the same filters straight away.
     */
    public Task nextInstance(Instant nextDueAt, String nextRrule) {
        Task next = new Task(title);
        next.describe(description);
        next.prioritise(priority);
        next.schedule(nextDueAt);
        next.estimate(estimateMinutes);
        next.moveTo(project);
        next.reorder(sortOrder);
        next.replaceTags(tags);
        next.repeat(nextRrule);
        return next;
    }

    /**
     * Attaches this task to a parent, enforcing the single level rule (FR-TSK-11).
     *
     * <p>Only two levels exist: a task, and its subtasks. A subtask can never become a parent,
     * which keeps every list and Kanban column renderable without recursion.
     */
    public void attachTo(Task parent) {
        if (parent == null) {
            this.parent = null;
            return;
        }
        if (parent.equals(this)) {
            throw new ValidationException("Task không thể là subtask của chính nó", "parentId");
        }
        if (parent.isSubtask()) {
            throw new SubtaskDepthException();
        }
        this.parent = parent;
    }

    /**
     * Moves to a new status, maintaining {@code completed_at} (FR-TSK-04).
     *
     * <p>The completion timestamp is derived state, never set by the caller: entering DONE stamps
     * it, leaving DONE clears it. That is what stops a reopened task from keeping a stale
     * completion date (T1-02, T1-03).
     */
    public void changeStatus(TaskStatus newStatus, Instant now) {
        if (newStatus == null) {
            throw new ValidationException("Trạng thái không hợp lệ", "status");
        }
        if (newStatus == this.status) {
            return;
        }
        this.status = newStatus;
        this.completedAt = newStatus.isDone() ? now : null;
    }

    /**
     * Whether the deadline has passed (FR-TSK-12).
     *
     * <p>Only DONE clears the flag, exactly as specified by T1-05 and T1-06. A CANCELLED task
     * past its deadline still reports true - see the note in PROGRESS.md.
     */
    public boolean isOverdue(Instant now) {
        return dueAt != null && dueAt.isBefore(now) && !status.isDone();
    }

    public void addTag(Tag tag) {
        if (tag != null) {
            tags.add(tag);
        }
    }

    public void replaceTags(Set<Tag> replacements) {
        tags.clear();
        if (replacements != null) {
            tags.addAll(replacements);
        }
    }

    /** Soft delete (FR-TSK-03). The row stays put so the undo toast can bring it back. */
    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isSubtask() {
        return parent != null;
    }

    public Project getProject() {
        return project;
    }

    public Task getParent() {
        return parent;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Priority getPriority() {
        return priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Integer getEstimateMinutes() {
        return estimateMinutes;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getRrule() {
        return rrule;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    /** Raised when nesting would exceed the one level allowed by FR-TSK-11. */
    public static class SubtaskDepthException extends DomainException {
        public SubtaskDepthException() {
            super("Không thể tạo subtask cho một subtask. Chỉ hỗ trợ một cấp.", "parentId");
        }
    }
}
