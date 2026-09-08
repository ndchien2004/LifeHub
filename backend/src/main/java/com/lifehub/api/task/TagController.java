package com.lifehub.api.task;

import com.lifehub.api.common.ApiResponse;
import com.lifehub.api.task.TaskDtos.CreateTagRequest;
import com.lifehub.api.task.TaskDtos.TagResponse;
import com.lifehub.api.task.TaskDtos.UpdateTagRequest;
import com.lifehub.application.task.TagService;
import com.lifehub.domain.task.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tag endpoints (06-API-SPEC.md section 4). */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;
    private final TaskMapper mapper;

    public TagController(TagService tagService, TaskMapper mapper) {
        this.tagService = tagService;
        this.mapper = mapper;
    }

    /** Usage counts come from one grouped query, not one lookup per tag. */
    @GetMapping
    public ApiResponse<List<TagResponse>> list() {
        Map<String, Long> usage = tagService.usageCounts();
        List<TagResponse> tags = tagService.findAll().stream()
                .map(tag -> mapper.toResponse(tag, usage.getOrDefault(tag.getId(), 0L)))
                .toList();
        return ApiResponse.ok(tags);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TagResponse> create(@Valid @RequestBody CreateTagRequest request) {
        Tag tag = tagService.create(request.name(), request.color());
        return ApiResponse.ok(mapper.toResponse(tag, 0));
    }

    @PatchMapping("/{id}")
    public ApiResponse<TagResponse> update(
            @PathVariable String id, @Valid @RequestBody UpdateTagRequest request) {
        Tag tag = tagService.update(id, request.name(), request.color());
        return ApiResponse.ok(mapper.toResponse(tag, tagService.usageCounts().getOrDefault(id, 0L)));
    }

    /** Hard delete, detaching the tag from every task through the join table cascade (item C-5b). */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        tagService.delete(id);
        return ApiResponse.ok(null);
    }
}
