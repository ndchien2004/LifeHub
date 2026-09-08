package com.lifehub.api.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifehub.api.common.ApiResponse;
import com.lifehub.api.task.TaskDtos.CreateProjectRequest;
import com.lifehub.api.task.TaskDtos.ProjectResponse;
import com.lifehub.application.task.ProjectService;
import com.lifehub.application.task.TaskCommands.CreateProject;
import com.lifehub.application.task.TaskCommands.Patch;
import com.lifehub.application.task.TaskCommands.UpdateProject;
import com.lifehub.application.task.TaskQueryService;
import com.lifehub.domain.task.Project;
import com.lifehub.domain.task.ProjectStatus;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Project endpoints (06-API-SPEC.md section 4). */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final TaskQueryService taskQueryService;
    private final TaskMapper mapper;
    private final ObjectMapper objectMapper;

    public ProjectController(
            ProjectService projectService,
            TaskQueryService taskQueryService,
            TaskMapper mapper,
            ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.taskQueryService = taskQueryService;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /** Each project carries its task totals so the UI can draw a progress bar (FR-PRJ-03). */
    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(
            @RequestParam(defaultValue = "true") boolean includeArchived) {
        List<ProjectResponse> projects = projectService.findAll(includeArchived).stream()
                .map(project -> mapper.toResponse(project, taskQueryService.countByProject(project.getId())))
                .toList();
        return ApiResponse.ok(projects);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> detail(@PathVariable String id) {
        Project project = projectService.findById(id);
        return ApiResponse.ok(mapper.toResponse(project, taskQueryService.countByProject(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        Project project = projectService.create(new CreateProject(
                request.name(), request.color(), request.description(), request.status()));
        return ApiResponse.ok(mapper.toResponse(project, taskQueryService.countByProject(project.getId())));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProjectResponse> update(@PathVariable String id, @RequestBody JsonNode body) {
        UpdateProject command = new UpdateProject(
                patch(body, "name", String.class),
                patch(body, "color", String.class),
                patch(body, "description", String.class),
                patch(body, "status", ProjectStatus.class));

        Project project = projectService.update(id, command);
        return ApiResponse.ok(mapper.toResponse(project, taskQueryService.countByProject(id)));
    }

    /** Deleting a project releases its tasks rather than destroying them (FR-PRJ-02). */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        projectService.delete(id);
        return ApiResponse.ok(null);
    }

    private <T> Patch<T> patch(JsonNode body, String field, Class<T> type) {
        if (body == null || !body.has(field)) {
            return Patch.absent();
        }
        JsonNode node = body.get(field);
        return node.isNull() ? Patch.of(null) : Patch.of(objectMapper.convertValue(node, type));
    }
}
