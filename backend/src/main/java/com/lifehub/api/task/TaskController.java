package com.lifehub.api.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifehub.api.common.ApiResponse;
import com.lifehub.api.common.PageResponse;
import com.lifehub.api.task.TaskDtos.ChangeStatusRequest;
import com.lifehub.api.task.TaskDtos.CreateTaskRequest;
import com.lifehub.api.task.TaskDtos.ReorderRequest;
import com.lifehub.api.task.TaskDtos.TaskResponse;
import com.lifehub.application.task.TaskCommands.CreateTask;
import com.lifehub.application.task.TaskCommands.Patch;
import com.lifehub.application.task.TaskCommands.ReorderEntry;
import com.lifehub.application.task.TaskCommands.UpdateTask;
import com.lifehub.application.task.TaskQueryService;
import com.lifehub.application.task.TaskService;
import com.lifehub.domain.common.Page;
import com.lifehub.domain.common.PageRequest;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.task.Priority;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskFilter;
import com.lifehub.domain.task.TaskRepository.Counts;
import com.lifehub.domain.task.TaskStatus;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
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

/** Task endpoints (06-API-SPEC.md section 3). */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskQueryService taskQueryService;
    private final TaskMapper mapper;
    private final ObjectMapper objectMapper;

    public TaskController(
            TaskService taskService,
            TaskQueryService taskQueryService,
            TaskMapper mapper,
            ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.taskQueryService = taskQueryService;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<PageResponse<TaskResponse>> list(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String tagIds,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) OffsetDateTime dueFrom,
            @RequestParam(required = false) OffsetDateTime dueTo,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "false") boolean topLevelOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort) {

        TaskFilter filter = new TaskFilter(
                projectId,
                csv(tagIds, Function.identity()),
                csv(status, value -> parseEnum(TaskStatus.class, value, "status")),
                csv(priority, value -> parseEnum(Priority.class, value, "priority")),
                mapper.toInstant(dueFrom),
                mapper.toInstant(dueTo),
                q,
                includeDeleted,
                topLevelOnly);

        Page<Task> result = taskQueryService.search(filter, pageRequest(page, size, sort));
        Map<String, Counts> subtaskCounts = taskQueryService.countSubtasks(result.items());

        return ApiResponse.ok(PageResponse.of(result, mapper.toResponses(result.items(), subtaskCounts)));
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> detail(@PathVariable String id) {
        Task task = taskQueryService.findById(id);
        List<Task> subtasks = taskQueryService.findSubtasks(id);
        Counts counts = taskQueryService.countSubtasks(List.of(task)).getOrDefault(id, Counts.EMPTY);
        return ApiResponse.ok(mapper.toResponse(task, counts, subtasks));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        Task created = taskService.create(new CreateTask(
                request.title(),
                request.description(),
                request.priority(),
                mapper.toInstant(request.dueAt()),
                request.projectId(),
                request.parentId(),
                request.tagIds(),
                request.estimateMinutes()));
        return ApiResponse.ok(detailOf(created.getId()));
    }

    /**
     * Partial update.
     *
     * <p>Read as a raw JSON tree rather than bound to a record, because PATCH has to tell an
     * omitted field from one explicitly sent as null. Clearing a due date and leaving it alone are
     * different requests that both look like a null field after binding.
     */
    @PatchMapping("/{id}")
    public ApiResponse<TaskResponse> update(@PathVariable String id, @RequestBody JsonNode body) {
        UpdateTask command = new UpdateTask(
                patch(body, "title", String.class),
                patch(body, "description", String.class),
                patch(body, "priority", Priority.class),
                patch(body, "status", TaskStatus.class),
                patchInstant(body, "dueAt"),
                patch(body, "projectId", String.class),
                patchStringList(body, "tagIds"),
                patch(body, "estimateMinutes", Integer.class));

        taskService.update(id, command);
        return ApiResponse.ok(detailOf(id));
    }

    /** Dedicated status endpoint, optimised for Kanban drag and drop (FR-TSK-07). */
    @PatchMapping("/{id}/status")
    public ApiResponse<TaskResponse> changeStatus(
            @PathVariable String id, @RequestBody ChangeStatusRequest request) {
        if (request.status() == null) {
            throw new ValidationException("Thiếu trạng thái cần chuyển", "status");
        }
        taskService.changeStatus(id, request.status());
        return ApiResponse.ok(detailOf(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        taskService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<TaskResponse> restore(@PathVariable String id) {
        taskService.restore(id);
        return ApiResponse.ok(detailOf(id));
    }

    @PatchMapping("/reorder")
    public ApiResponse<Void> reorder(@RequestBody ReorderRequest request) {
        List<ReorderEntry> entries = request.items() == null
                ? List.of()
                : request.items().stream()
                        .map(item -> new ReorderEntry(item.taskId(), item.sortOrder()))
                        .toList();
        taskService.reorder(entries);
        return ApiResponse.ok(null);
    }

    private TaskResponse detailOf(String id) {
        Task task = taskQueryService.findById(id);
        Counts counts = taskQueryService.countSubtasks(List.of(task)).getOrDefault(id, Counts.EMPTY);
        return mapper.toResponse(task, counts, null);
    }

    /** Parses the {@code field,asc} / {@code field,desc} convention from 06-API-SPEC.md section 1. */
    private PageRequest pageRequest(int page, int size, String sort) {
        if (sort == null || sort.isBlank()) {
            return new PageRequest(page, size, null, true);
        }
        String[] parts = sort.split(",", 2);
        boolean ascending = parts.length < 2 || !"desc".equalsIgnoreCase(parts[1].trim());
        return new PageRequest(page, size, parts[0].trim(), ascending);
    }

    private <T> List<T> csv(String raw, Function<String, T> converter) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(converter)
                .toList();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Giá trị không hợp lệ: " + value, field);
        }
    }

    private <T> Patch<T> patch(JsonNode body, String field, Class<T> type) {
        if (body == null || !body.has(field)) {
            return Patch.absent();
        }
        JsonNode node = body.get(field);
        return node.isNull() ? Patch.of(null) : Patch.of(objectMapper.convertValue(node, type));
    }

    private Patch<Instant> patchInstant(JsonNode body, String field) {
        Patch<OffsetDateTime> raw = patch(body, field, OffsetDateTime.class);
        return raw.present() ? Patch.of(mapper.toInstant(raw.value())) : Patch.absent();
    }

    private Patch<List<String>> patchStringList(JsonNode body, String field) {
        if (body == null || !body.has(field)) {
            return Patch.absent();
        }
        JsonNode node = body.get(field);
        if (node.isNull()) {
            return Patch.of(List.of());
        }
        return Patch.of(objectMapper.convertValue(
                node, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
    }
}
