package com.lifehub.application.task;

import com.lifehub.application.task.TaskCommands.CreateProject;
import com.lifehub.application.task.TaskCommands.UpdateProject;
import com.lifehub.domain.common.ConflictException;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.task.Project;
import com.lifehub.domain.task.ProjectRepository;
import com.lifehub.domain.task.TaskRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Project management (FR-PRJ-01 → FR-PRJ-03). */
@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final Clock clock;

    public ProjectService(ProjectRepository projectRepository, TaskRepository taskRepository, Clock clock) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Project> findAll(boolean includeArchived) {
        return projectRepository.findAll(includeArchived);
    }

    @Transactional(readOnly = true)
    public Project findById(String id) {
        return projectRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy dự án"));
    }

    public Project create(CreateProject command) {
        requireNameAvailable(command.name(), null);
        return projectRepository.save(
                new Project(command.name(), command.color(), command.description(), command.status()));
    }

    public Project update(String id, UpdateProject command) {
        Project project = findById(id);

        command.name().ifPresent(name -> {
            requireNameAvailable(name, id);
            project.rename(name);
        });
        command.color().ifPresent(project::recolor);
        command.description().ifPresent(project::describe);
        command.status().ifPresent(project::changeStatus);

        return projectRepository.save(project);
    }

    /**
     * Soft deletes a project and releases its tasks (FR-PRJ-02).
     *
     * <p>Deleting a container must never destroy its contents. The tasks stay exactly as they
     * are and simply become project-less, which is why the foreign key is cleared explicitly:
     * the row is only soft deleted, so the ON DELETE SET NULL rule never fires.
     */
    public void delete(String id) {
        Project project = findById(id);
        taskRepository.clearProject(id);
        project.softDelete(clock.instant());
        projectRepository.save(project);
    }

    private void requireNameAvailable(String name, String excludingId) {
        if (name != null && projectRepository.existsByName(name.trim(), excludingId)) {
            throw new ConflictException("Tên dự án đã tồn tại", "name");
        }
    }
}
