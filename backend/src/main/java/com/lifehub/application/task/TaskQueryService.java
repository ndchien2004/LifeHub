package com.lifehub.application.task;

import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.common.Page;
import com.lifehub.domain.common.PageRequest;
import com.lifehub.domain.task.Project;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskFilter;
import com.lifehub.domain.task.TaskRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the task module (FR-TSK-07 → FR-TSK-10).
 *
 * <p>{@code open-in-view} is switched off, so the persistence session ends when a method here
 * returns. Anything the api layer will need must therefore be loaded before that happens - hence
 * the explicit hydration below rather than letting the mapper trip over a lazy proxy.
 */
@Service
@Transactional(readOnly = true)
public class TaskQueryService {

    private final TaskRepository taskRepository;

    public TaskQueryService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Page<Task> search(TaskFilter filter, PageRequest pageRequest) {
        Page<Task> page = taskRepository.search(filter, pageRequest);
        hydrate(page.items());
        return page;
    }

    public Task findById(String id) {
        Task task = taskRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy task"));
        hydrate(List.of(task));
        return task;
    }

    public List<Task> findSubtasks(String parentId) {
        List<Task> subtasks = taskRepository.findSubtasks(parentId);
        hydrate(subtasks);
        return subtasks;
    }

    /** Subtask totals for a whole page of tasks, in one query rather than one per row. */
    public Map<String, TaskRepository.Counts> countSubtasks(List<Task> tasks) {
        return taskRepository.countSubtasks(tasks.stream().map(Task::getId).toList());
    }

    /** Total and completed live task counts, backing the project progress bar (FR-PRJ-03). */
    public TaskRepository.Counts countByProject(String projectId) {
        return taskRepository.countByProject(projectId);
    }

    /**
     * Forces the lazy associations the response needs while the session is still open.
     *
     * <p>Both are batch loaded (see the {@code @BatchSize} annotations on {@code Project} and
     * {@code Tag}), so a page of tasks costs a couple of extra queries rather than one per row.
     */
    private void hydrate(List<Task> tasks) {
        for (Task task : tasks) {
            Project project = task.getProject();
            if (project != null) {
                project.getName();
            }
            task.getTags().size();
        }
    }
}
