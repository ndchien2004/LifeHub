package com.lifehub.api.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifehub.api.common.ApiResponse;
import com.lifehub.api.common.JsonPatchReader;
import com.lifehub.api.finance.FinanceDtos.CreateRecurringRuleRequest;
import com.lifehub.api.finance.FinanceDtos.CreateTransactionRequest;
import com.lifehub.api.finance.FinanceDtos.RecurringRuleResponse;
import com.lifehub.api.finance.FinanceDtos.TransactionResponse;
import com.lifehub.application.finance.FinanceCommands.CreateRecurringRule;
import com.lifehub.application.finance.FinanceCommands.CreateTransaction;
import com.lifehub.application.finance.FinanceCommands.UpdateRecurringRule;
import com.lifehub.application.finance.RecurringTransactionService;
import com.lifehub.domain.system.RecurringRule;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recurring transaction rules (FR-FIN-13).
 *
 * <p>06-API-SPEC.md has no section for these - the requirement is in Phase 3 scope but the endpoint
 * table stops at budgets. The paths follow the same conventions as every other resource in the
 * spec, and the deviation is recorded in PROGRESS.md.
 */
@RestController
@RequestMapping("/api/v1/recurring-rules")
public class RecurringRuleController {

    private final RecurringTransactionService recurringTransactionService;
    private final FinanceMapper mapper;
    private final JsonPatchReader patches;

    public RecurringRuleController(
            RecurringTransactionService recurringTransactionService,
            FinanceMapper mapper,
            JsonPatchReader patches) {
        this.recurringTransactionService = recurringTransactionService;
        this.mapper = mapper;
        this.patches = patches;
    }

    @GetMapping
    public ApiResponse<List<RecurringRuleResponse>> list() {
        return ApiResponse.ok(recurringTransactionService.findAll().stream()
                .map(this::toResponse)
                .toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RecurringRuleResponse> create(
            @Valid @RequestBody CreateRecurringRuleRequest request) {
        RecurringRule rule = recurringTransactionService.create(
                new CreateRecurringRule(request.rrule(), request.startDate(), toCommand(request.template())));
        return ApiResponse.ok(toResponse(rule));
    }

    @PatchMapping("/{id}")
    public ApiResponse<RecurringRuleResponse> update(
            @PathVariable String id, @RequestBody JsonNode body) {
        UpdateRecurringRule command = new UpdateRecurringRule(
                patches.read(body, "rrule", String.class),
                patches.read(body, "isActive", Boolean.class),
                patches.read(body, "nextRunDate", LocalDate.class));

        return ApiResponse.ok(toResponse(recurringTransactionService.update(id, command)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        recurringTransactionService.delete(id);
        return ApiResponse.ok(null);
    }

    /**
     * Runs every rule that has come due, right now.
     *
     * <p>The scheduler already does this at startup and hourly; this endpoint exists so the user
     * can force it from the UI rather than restarting the app, and so the behaviour is reachable
     * from an integration test without waiting an hour.
     */
    @PostMapping("/run")
    public ApiResponse<List<TransactionResponse>> run() {
        return ApiResponse.ok(mapper.toResponses(recurringTransactionService.runDue()));
    }

    private RecurringRuleResponse toResponse(RecurringRule rule) {
        return mapper.toResponse(rule, recurringTransactionService.templateOf(rule));
    }

    private CreateTransaction toCommand(CreateTransactionRequest request) {
        return new CreateTransaction(
                request.type(),
                request.amount(),
                request.walletId(),
                request.toWalletId(),
                request.categoryId(),
                request.note(),
                mapper.toInstant(request.occurredAt()),
                request.tagIds(),
                request.source(),
                request.aiConfidence(),
                null,
                null);
    }
}
