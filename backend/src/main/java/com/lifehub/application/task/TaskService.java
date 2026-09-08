package com.lifehub.application.task;

import com.lifehub.application.calendar.RecurrenceExpander;
import com.lifehub.application.task.TaskCommands.CreateTask;
import com.lifehub.application.task.TaskCommands.ReorderEntry;
import com.lifehub.application.task.TaskCommands.UpdateTask;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.task.Project;
import com.lifehub.domain.task.ProjectRepository;
import com.lifehub.domain.task.Tag;
import com.lifehub.domain.task.TagRepository;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskRepository;
import com.lifehub.domain.task.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write side of the task module (FR-TSK-01 → FR-TSK-06, FR-TSK-11).
 *
 * <p>Reads live in {@link TaskQueryService}. Splitting them keeps every method here inside a
 * read-write transaction while queries stay read-only, and stops the filtering logic from
 * growing into the class that also mutates state.
 *
 * <p>Time comes from an injected {@link Clock} rather than {@code Instant.now()} so scheduling
 * and completion behaviour is testable against a fixed clock (08-TEST-PLAN.md §2).
 */
@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TagRepository tagRepository;
    private final RecurrenceExpander expander;
    private final Clock clock;
    private final ZoneId displayZone;

    public TaskService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            TagRepository tagRepository,
            RecurrenceExpander expander,
            Clock clock,
            ZoneId displayZone) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.tagRepository = tagRepository;
        this.expander = expander;
        this.clock = clock;
        this.displayZone = displayZone;
    }

    public Task create(CreateTask command) {
        Task task = new Task(command.title());
        task.describe(command.description());
        task.prioritise(command.priority());
        task.schedule(command.dueAt());
        task.estimate(command.estimateMinutes());
        task.moveTo(resolveProject(command.projectId()));
        task.attachTo(resolveParent(command.parentId()));
        task.replaceTags(resolveTags(command.tagIds()));
        expander.validate(command.rrule());
        task.repeat(command.rrule());
        return taskRepository.save(task);
    }

    public Task update(String id, UpdateTask command) {
        Task task = require(id);

        command.title().ifPresent(task::retitle);
        command.description().ifPresent(task::describe);
        command.priority().ifPresent(task::prioritise);
        command.dueAt().ifPresent(task::schedule);
        command.estimateMinutes().ifPresent(task::estimate);
        command.projectId().ifPresent(projectId -> task.moveTo(resolveProject(projectId)));
        command.tagIds().ifPresent(tagIds -> task.replaceTags(resolveTags(tagIds)));
        command.rrule().ifPresent(rrule -> {
            expander.validate(rrule);
            task.repeat(rrule);
        });
        command.status().ifPresent(status -> task.changeStatus(status, now()));

        Task saved = taskRepository.save(task);
        command.status().ifPresent(status -> spawnNextInstance(saved, status));
        return saved;
    }

    /** Dedicated status change, kept cheap because Kanban drag and drop calls it constantly. */
    public Task changeStatus(String id, TaskStatus status) {
        Task task = require(id);
        task.changeStatus(status, now());
        Task saved = taskRepository.save(task);
        spawnNextInstance(saved, status);
        return saved;
    }

    /**
     * Rolls a repeating task forward on completion (FR-TSK-13).
     *
     * <p>The completed task is left exactly as it is - it becomes the record of that run - and a
     * fresh TODO copy is created for the next deadline. Generating on completion rather than
     * ahead of time means the list never fills with future instances the user has not reached.
     *
     * @return the successor, or empty when the task does not repeat or its rule is spent
     */
    public Optional<Task> spawnNextInstance(Task task, TaskStatus newStatus) {
        if (newStatus == null || !newStatus.isDone() || !task.isRepeating()) {
            return Optional.empty();
        }
        Optional<String> nextRule = expander.consumeOne(task.getRrule());
        if (nextRule.isEmpty()) {
            return Optional.empty();
        }
        return expander
                .nextAfter(task.getRrule(), task.getDueAt(), displayZone, task.getDueAt())
                .map(nextDueAt -> taskRepository.save(task.nextInstance(nextDueAt, nextRule.get())));
    }

    /** Soft delete (FR-TSK-03). The row survives so the undo toast can restore it. */
    public void delete(String id) {
        Task task = require(id);
        Instant now = now();
        task.softDelete(now);
        taskRepository.save(task);

        // A parent disappearing while its subtasks stay visible would orphan them in the UI.
        for (Task subtask : taskRepository.findSubtasks(id)) {
            subtask.softDelete(now);
            taskRepository.save(subtask);
        }
    }

    public Task restore(String id) {
        Task task = taskRepository
                .findByIdIncludingDeleted(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy task cần khôi phục"));
        task.restore();
        Task restored = taskRepository.save(task);

        // Must include deleted rows: these subtasks were soft deleted alongside the parent, so the
        // live query can no longer see the very rows that need bringing back.
        for (Task subtask : taskRepository.findSubtasksIncludingDeleted(id)) {
            subtask.restore();
            taskRepository.save(subtask);
        }
        return restored;
    }

    /** Bulk sort order update, issued once when a Kanban drag settles. */
    public void reorder(List<ReorderEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<String> ids = entries.stream().map(ReorderEntry::taskId).toList();
        List<Task> tasks = taskRepository.findAllById(ids);

        for (ReorderEntry entry : entries) {
            tasks.stream()
                    .filter(task -> task.getId().equals(entry.taskId()))
                    .findFirst()
                    .ifPresent(task -> task.reorder(entry.sortOrder()));
        }
        tasks.forEach(taskRepository::save);
    }

    private Task require(String id) {
        return taskRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy task"));
    }

    private Project resolveProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        return projectRepository
                .findById(projectId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy dự án"));
    }

    private Task resolveParent(String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return null;
        }
        return taskRepository
                .findById(parentId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy task cha"));
    }

    private Set<Tag> resolveTags(List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Set.of();
        }
        Set<String> requested = new LinkedHashSet<>(tagIds);
        Set<Tag> found = tagRepository.findAllById(requested);
        if (found.size() != requested.size()) {
            throw new ValidationException("Có nhãn không tồn tại", "tagIds");
        }
        return found;
    }

    private Instant now() {
        return clock.instant();
    }
}
