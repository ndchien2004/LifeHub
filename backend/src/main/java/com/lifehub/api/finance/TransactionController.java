package com.lifehub.api.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifehub.api.common.ApiResponse;
import com.lifehub.api.common.JsonPatchReader;
import com.lifehub.api.common.PageResponse;
import com.lifehub.api.finance.FinanceDtos.CreateTransactionRequest;
import com.lifehub.api.finance.FinanceDtos.SummaryResponse;
import com.lifehub.api.finance.FinanceDtos.TransactionResponse;
import com.lifehub.api.finance.FinanceDtos.TransactionWriteResponse;
import com.lifehub.application.finance.FinanceCommands.CreateTransaction;
import com.lifehub.application.finance.FinanceCommands.UpdateTransaction;
import com.lifehub.application.finance.TransactionQueryService;
import com.lifehub.application.finance.TransactionService;
import com.lifehub.domain.common.Page;
import com.lifehub.domain.common.PageRequest;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.finance.MoneyTransaction;
import com.lifehub.domain.finance.SummaryGroupBy;
import com.lifehub.domain.finance.TransactionFilter;
import com.lifehub.domain.finance.TransactionSource;
import com.lifehub.domain.finance.TransactionType;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

/** Transaction endpoints (06-API-SPEC.md section 7, SD-01). */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionQueryService queryService;
    private final FinanceMapper mapper;
    private final JsonPatchReader patches;

    public TransactionController(
            TransactionService transactionService,
            TransactionQueryService queryService,
            FinanceMapper mapper,
            JsonPatchReader patches) {
        this.transactionService = transactionService;
        this.queryService = queryService;
        this.mapper = mapper;
        this.patches = patches;
    }

    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> list(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String walletIds,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String tagIds,
            @RequestParam(required = false) Long minAmount,
            @RequestParam(required = false) Long maxAmount,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort) {

        TransactionFilter filter = new TransactionFilter(
                mapper.toInstant(from),
                mapper.toInstant(to),
                csv(walletIds, Function.identity()),
                csv(categoryIds, Function.identity()),
                csv(type, value -> parseEnum(TransactionType.class, value, "type")),
                csv(source, value -> parseEnum(TransactionSource.class, value, "source")),
                csv(tagIds, Function.identity()),
                minAmount,
                maxAmount,
                q,
                includeDeleted);

        Page<MoneyTransaction> result = queryService.search(filter, pageRequest(page, size, sort));
        return ApiResponse.ok(PageResponse.of(result, mapper.toResponses(result.items())));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> detail(@PathVariable String id) {
        return ApiResponse.ok(mapper.toResponse(queryService.findById(id)));
    }

    /** Totals for the pie and line charts (FR-FIN-10, FR-FIN-11). */
    @GetMapping("/summary")
    public ApiResponse<SummaryResponse> summary(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String type) {

        return ApiResponse.ok(mapper.toResponse(queryService.summarize(
                mapper.toInstant(from),
                mapper.toInstant(to),
                groupBy == null ? null : parseEnum(SummaryGroupBy.class, groupBy, "groupBy"),
                type == null ? null : parseEnum(TransactionType.class, type, "type"))));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionWriteResponse> create(
            @Valid @RequestBody CreateTransactionRequest request) {
        return ApiResponse.ok(mapper.toResponse(transactionService.create(toCommand(request))));
    }

    /**
     * Partial update.
     *
     * <p>Read as a raw JSON tree for the same reason as tasks: PATCH must tell a field the caller
     * omitted from one deliberately set to null, and clearing a note is a different request from
     * leaving it alone.
     */
    @PatchMapping("/{id}")
    public ApiResponse<TransactionWriteResponse> update(
            @PathVariable String id, @RequestBody JsonNode body) {
        UpdateTransaction command = new UpdateTransaction(
                patches.read(body, "type", TransactionType.class),
                patches.read(body, "amount", Long.class),
                patches.read(body, "walletId", String.class),
                patches.read(body, "toWalletId", String.class),
                patches.read(body, "categoryId", String.class),
                patches.read(body, "note", String.class),
                patches.readInstant(body, "occurredAt"),
                patches.readList(body, "tagIds", String.class));

        return ApiResponse.ok(mapper.toResponse(transactionService.update(id, command)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<TransactionWriteResponse> delete(@PathVariable String id) {
        return ApiResponse.ok(mapper.toResponse(transactionService.delete(id)));
    }

    /** Brings back a soft deleted transaction, backing the five second undo toast (NFR-USE-04). */
    @PostMapping("/{id}/restore")
    public ApiResponse<TransactionWriteResponse> restore(@PathVariable String id) {
        return ApiResponse.ok(mapper.toResponse(transactionService.restore(id)));
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

    private PageRequest pageRequest(int page, int size, String sort) {
        if (sort == null || sort.isBlank()) {
            return new PageRequest(page, size, null, false);
        }
        String[] parts = sort.split(",");
        boolean ascending = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim());
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
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Giá trị không hợp lệ: " + value, field);
        }
    }
}
