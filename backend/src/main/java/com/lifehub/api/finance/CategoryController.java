package com.lifehub.api.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifehub.api.common.ApiResponse;
import com.lifehub.api.common.JsonPatchReader;
import com.lifehub.api.finance.FinanceDtos.CategoryResponse;
import com.lifehub.api.finance.FinanceDtos.CreateCategoryRequest;
import com.lifehub.application.finance.CategoryService;
import com.lifehub.application.finance.FinanceCommands.CreateCategory;
import com.lifehub.application.finance.FinanceCommands.UpdateCategory;
import com.lifehub.domain.finance.Category;
import com.lifehub.domain.finance.CategoryType;
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

/** Category endpoints (06-API-SPEC.md section 7). */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final FinanceMapper mapper;
    private final JsonPatchReader patches;

    public CategoryController(
            CategoryService categoryService, FinanceMapper mapper, JsonPatchReader patches) {
        this.categoryService = categoryService;
        this.mapper = mapper;
        this.patches = patches;
    }

    /** The two level tree, optionally narrowed to income or expense. */
    @GetMapping
    public ApiResponse<List<CategoryResponse>> list(@RequestParam(required = false) CategoryType type) {
        return ApiResponse.ok(mapper.toTree(categoryService.findAll(type)));
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> detail(@PathVariable String id) {
        Category category = categoryService.findById(id);
        return ApiResponse.ok(mapper.toResponse(category, List.of()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        Category category = categoryService.create(new CreateCategory(
                request.name(),
                request.type(),
                request.parentId(),
                request.icon(),
                request.color(),
                request.sortOrder()));
        return ApiResponse.ok(mapper.toResponse(category, List.of()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable String id, @RequestBody JsonNode body) {
        UpdateCategory command = new UpdateCategory(
                patches.read(body, "name", String.class),
                patches.read(body, "parentId", String.class),
                patches.read(body, "icon", String.class),
                patches.read(body, "color", String.class),
                patches.read(body, "sortOrder", Integer.class));

        return ApiResponse.ok(mapper.toResponse(categoryService.update(id, command), List.of()));
    }

    /** Soft delete, refused with 409 for a system category or one still in use. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        categoryService.delete(id);
        return ApiResponse.ok(null);
    }
}
