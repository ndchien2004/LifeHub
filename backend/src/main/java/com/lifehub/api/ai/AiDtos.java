package com.lifehub.api.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifehub.domain.ai.AiErrorCode;
import com.lifehub.domain.ai.ParseIntent;
import com.lifehub.domain.ai.ParseSource;
import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.TransactionType;
import com.lifehub.domain.task.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Request and response shapes for the AI endpoints (06-API-SPEC.md 8). */
public final class AiDtos {

    private AiDtos() {
    }

    // ---------- requests ----------

    /** {@code POST /ai/parse}. */
    public record ParseRequest(
            @NotBlank(message = "Hãy nhập nội dung cần phân tích")
            @Size(max = 500, message = "Nội dung tối đa 500 ký tự")
            String text) {
    }

    /** {@code POST /ai/suggest-category}. */
    public record SuggestCategoryRequest(
            @NotBlank(message = "Ghi chú không được để trống")
            @Size(max = 200, message = "Ghi chú tối đa 200 ký tự")
            String note,
            CategoryType type) {
    }

    // ---------- responses ----------

    /**
     * What {@code POST /ai/parse} returns.
     *
     * <p>Exactly one of the three drafts is populated. {@code source} is {@code RULE} whenever the
     * offline parser produced the answer, and {@code warning} says why - together they are what the
     * banner in the palette is driven by (FR-AI-08).
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ParseResponse(
            ParseIntent intent,
            double confidence,
            ParseSource source,
            TransactionDraftResponse transaction,
            TaskDraftResponse task,
            EventDraftResponse event,
            AiErrorCode warning) {
    }

    /**
     * A proposed transaction. Nothing has been saved; the ids are lookups into data the user
     * already owns.
     */
    public record TransactionDraftResponse(
            TransactionType type,
            Long amount,
            String categoryId,
            String categoryName,
            String walletId,
            String walletName,
            String note,
            OffsetDateTime occurredAt,
            Map<String, Double> fieldConfidence) {
    }

    public record TaskDraftResponse(
            String title,
            Priority priority,
            OffsetDateTime dueAt,
            String projectId,
            String projectName,
            Map<String, Double> fieldConfidence) {
    }

    public record EventDraftResponse(
            String title,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String location,
            List<Integer> reminderOffsetMinutes,
            Map<String, Double> fieldConfidence) {
    }

    /** {@code POST /ai/suggest-category}, at most three entries (UC-10 step 4). */
    public record SuggestCategoryResponse(List<CategorySuggestionResponse> suggestions) {
    }

    public record CategorySuggestionResponse(
            String categoryId, String categoryName, double confidence) {
    }

    /** {@code GET /ai/status}. */
    public record AiStatusResponse(
            boolean enabled, boolean configured, String model, boolean available) {
    }

    /** {@code POST /ai/test-connection}. Only ever returned when the probe succeeded. */
    public record TestConnectionResponse(boolean ok, String model) {
    }

    /** One row of {@code GET /ai/logs} (FR-AI-10). Never carries the API key or the prompt. */
    public record AiLogResponse(
            String id,
            String requestType,
            String inputText,
            String outputJson,
            ParseIntent intent,
            boolean success,
            AiErrorCode errorCode,
            Long latencyMs,
            Integer tokenInput,
            Integer tokenOutput,
            String model,
            OffsetDateTime createdAt) {
    }
}
