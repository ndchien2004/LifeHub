package com.lifehub.api.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifehub.api.common.ApiResponse;
import com.lifehub.api.common.JsonPatchReader;
import com.lifehub.api.finance.FinanceDtos.BudgetResponse;
import com.lifehub.api.finance.FinanceDtos.CreateBudgetRequest;
import com.lifehub.application.finance.BudgetService;
import com.lifehub.application.finance.FinanceCommands.CreateBudget;
import com.lifehub.application.finance.FinanceCommands.UpdateBudget;
import com.lifehub.domain.finance.BudgetPeriod;
import jakarta.validation.Valid;
import java.time.LocalDate;
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

/** Budget endpoints (06-API-SPEC.md section 7). */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;
    private final FinanceMapper mapper;
    private final JsonPatchReader patches;

    public BudgetController(BudgetService budgetService, FinanceMapper mapper, JsonPatchReader patches) {
        this.budgetService = budgetService;
        this.mapper = mapper;
        this.patches = patches;
    }

    /** Budgets with usage for the cycle happening right now (FR-FIN-09). */
    @GetMapping
    public ApiResponse<List<BudgetResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(
                budgetService.findAll(includeInactive).stream().map(mapper::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<BudgetResponse> detail(@PathVariable String id) {
        return ApiResponse.ok(mapper.toResponse(budgetService.findById(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BudgetResponse> create(@Valid @RequestBody CreateBudgetRequest request) {
        return ApiResponse.ok(mapper.toResponse(budgetService.create(new CreateBudget(
                request.categoryId(),
                request.limitAmount(),
                request.period(),
                request.startDate(),
                request.isActive()))));
    }

    @PatchMapping("/{id}")
    public ApiResponse<BudgetResponse> update(@PathVariable String id, @RequestBody JsonNode body) {
        UpdateBudget command = new UpdateBudget(
                patches.read(body, "categoryId", String.class),
                patches.read(body, "limitAmount", Long.class),
                patches.read(body, "period", BudgetPeriod.class),
                patches.read(body, "startDate", LocalDate.class),
                patches.read(body, "isActive", Boolean.class));

        return ApiResponse.ok(mapper.toResponse(budgetService.update(id, command)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        budgetService.delete(id);
        return ApiResponse.ok(null);
    }
}
