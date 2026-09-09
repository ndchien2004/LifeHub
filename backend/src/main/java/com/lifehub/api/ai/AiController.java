package com.lifehub.api.ai;

import com.lifehub.api.ai.AiDtos.AiLogResponse;
import com.lifehub.api.ai.AiDtos.AiStatusResponse;
import com.lifehub.api.ai.AiDtos.ParseRequest;
import com.lifehub.api.ai.AiDtos.ParseResponse;
import com.lifehub.api.ai.AiDtos.SuggestCategoryRequest;
import com.lifehub.api.ai.AiDtos.SuggestCategoryResponse;
import com.lifehub.api.ai.AiDtos.TestConnectionResponse;
import com.lifehub.api.common.ApiResponse;
import com.lifehub.api.common.PageResponse;
import com.lifehub.application.ai.AiLogService;
import com.lifehub.application.ai.AiStatusService;
import com.lifehub.application.ai.CategorySuggestService;
import com.lifehub.application.ai.NlParseService;
import com.lifehub.domain.common.PageRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI endpoints (06-API-SPEC.md 8).
 *
 * <p>Every one of these is read only in the business sense. {@code /ai/parse} returns a draft and
 * nothing else; if the user presses Escape on the form it filled in, no record was ever created,
 * because none of these paths can create one (AGENTS.md 3.4 rule 1).
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final NlParseService nlParseService;
    private final CategorySuggestService categorySuggestService;
    private final AiStatusService statusService;
    private final AiLogService logService;
    private final AiMapper mapper;

    public AiController(
            NlParseService nlParseService,
            CategorySuggestService categorySuggestService,
            AiStatusService statusService,
            AiLogService logService,
            AiMapper mapper) {
        this.nlParseService = nlParseService;
        this.categorySuggestService = categorySuggestService;
        this.statusService = statusService;
        this.logService = logService;
        this.mapper = mapper;
    }

    /** Reads a free-form Vietnamese sentence into a draft the user then confirms (UC-09). */
    @PostMapping("/parse")
    public ApiResponse<ParseResponse> parse(@Valid @RequestBody ParseRequest request) {
        return ApiResponse.ok(mapper.toResponse(nlParseService.parse(request.text())));
    }

    /** Up to three categories for a note (UC-10). An empty list is a normal answer. */
    @PostMapping("/suggest-category")
    public ApiResponse<SuggestCategoryResponse> suggestCategory(
            @Valid @RequestBody SuggestCategoryRequest request) {

        var suggestions = categorySuggestService.suggest(request.note(), request.type()).stream()
                .map(mapper::toResponse)
                .toList();
        return ApiResponse.ok(new SuggestCategoryResponse(suggestions));
    }

    /** Whether AI is on, configured, and which model would be used. Costs no API call. */
    @GetMapping("/status")
    public ApiResponse<AiStatusResponse> status() {
        return ApiResponse.ok(mapper.toResponse(statusService.status()));
    }

    /** Spends one real request to prove the key works. Only ever called from the Settings screen. */
    @PostMapping("/test-connection")
    public ApiResponse<TestConnectionResponse> testConnection() {
        return ApiResponse.ok(new TestConnectionResponse(true, statusService.testConnection()));
    }

    /** The call log, newest first, for debugging a result that came out wrong (FR-AI-10). */
    @GetMapping("/logs")
    public ApiResponse<PageResponse<AiLogResponse>> logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        var logs = logService.findRecent(PageRequest.of(page, size));
        return ApiResponse.ok(
                PageResponse.of(logs, logs.items().stream().map(mapper::toResponse).toList()));
    }
}
