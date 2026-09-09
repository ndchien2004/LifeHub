package com.lifehub.application.ai;

import com.lifehub.domain.ai.AiClient;
import com.lifehub.domain.ai.AiClient.AiCompletion;
import com.lifehub.domain.ai.AiClient.AiRequest;
import com.lifehub.domain.ai.AiException;
import com.lifehub.domain.ai.AiRequestType;
import com.lifehub.domain.ai.AiResponseReader;
import com.lifehub.domain.ai.CategorySuggestion;
import com.lifehub.domain.ai.ParseContext;
import com.lifehub.domain.ai.ParseContext.RecentTransaction;
import com.lifehub.domain.ai.PromptTemplates;
import com.lifehub.domain.ai.PromptTemplates.Prompt;
import com.lifehub.domain.finance.CategoryType;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Proposes up to three categories for a transaction note (FR-AI-07, UC-10).
 *
 * <p>Two rules from the use case shape this service. An exact match against the user's own history
 * skips the model entirely (UC-10 exception E2) - they have already answered this question, and
 * asking again costs money and latency for a worse answer. And any failure is swallowed (UC-10
 * exception E1): this is a set of chips under a form field, so no suggestion is a perfectly good
 * outcome and an error toast would be an interruption the user did not ask for.
 */
@Service
public class CategorySuggestService {

    private static final Logger log = LoggerFactory.getLogger(CategorySuggestService.class);

    private static final String TEMPLATE = "category-suggest";
    private static final int MAX_NOTE_LENGTH = 200;

    /** Certainty attached to a match taken straight from the user's own history. */
    private static final double EXACT_MATCH_CONFIDENCE = 0.95;

    private final AiSettings settings;
    private final AiContextProvider contextProvider;
    private final PromptTemplates prompts;
    private final AiClient aiClient;
    private final AiResponseReader reader;
    private final AiLogService logService;

    public CategorySuggestService(
            AiSettings settings,
            AiContextProvider contextProvider,
            PromptTemplates prompts,
            AiClient aiClient,
            AiResponseReader reader,
            AiLogService logService) {
        this.settings = settings;
        this.contextProvider = contextProvider;
        this.prompts = prompts;
        this.aiClient = aiClient;
        this.reader = reader;
        this.logService = logService;
    }

    /** Suggestions for {@code note}, or an empty list when there is nothing worth showing. */
    public List<CategorySuggestion> suggest(String note, CategoryType type) {
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NOTE_LENGTH);
        }

        ParseContext context = contextProvider.load();
        CategoryType effectiveType = type == null ? CategoryType.EXPENSE : type;

        Optional<CategorySuggestion> remembered = fromHistory(trimmed, effectiveType, context);
        if (remembered.isPresent()) {
            return List.of(remembered.get());
        }

        if (!settings.isUsable()) {
            return List.of();
        }
        return fromAi(trimmed, effectiveType, context);
    }

    /**
     * The category of the most recent transaction whose note reads the same (UC-10 exception E2).
     *
     * <p>Comparison is on the accent folded note, so "Trà sữa" and "tra sua" are one and the same
     * habit. Only an exact match counts: a partial one is a guess, and guessing is the model's job.
     */
    private Optional<CategorySuggestion> fromHistory(
            String note, CategoryType type, ParseContext context) {

        String needle = ParseContext.normalize(note);

        for (RecentTransaction row : context.recentTransactions()) {
            if (row.categoryName() == null || !ParseContext.normalize(row.note()).equals(needle)) {
                continue;
            }
            Optional<ParseContext.CategoryOption> category =
                    context.findCategory(leafOf(row.categoryName()), type);
            if (category.isPresent()) {
                return category.map(option -> new CategorySuggestion(
                        option.id(), option.label(), EXACT_MATCH_CONFIDENCE));
            }
        }
        return Optional.empty();
    }

    private List<CategorySuggestion> fromAi(
            String note, CategoryType type, ParseContext context) {

        long startedAt = System.nanoTime();
        try {
            Prompt prompt = prompts.build(
                    TEMPLATE, AiPromptVariables.forCategorySuggest(note, type, context));

            AiCompletion completion = aiClient.complete(
                    new AiRequest(settings.model(), prompt.system(), prompt.user(), null, 0));

            List<CategorySuggestion> suggestions =
                    reader.readCategorySuggestions(completion.text(), context);

            logService.recordSuccess(
                    AiRequestType.CATEGORY_SUGGEST,
                    note,
                    null,
                    completion.text(),
                    completion.model(),
                    elapsedMs(startedAt),
                    completion.inputTokens(),
                    completion.outputTokens());

            return suggestions;
        } catch (AiException e) {
            // Silently: this is a convenience, not something to interrupt the user over.
            log.debug("Không lấy được gợi ý danh mục", e);
            logService.recordFailure(
                    AiRequestType.CATEGORY_SUGGEST,
                    note,
                    e.getErrorCode(),
                    settings.model(),
                    elapsedMs(startedAt));
            return List.of();
        }
    }

    /** Takes "Cà phê" out of the stored label "Ăn uống › Cà phê". */
    private String leafOf(String label) {
        int separator = label.lastIndexOf('›');
        return separator < 0 ? label : label.substring(separator + 1).trim();
    }

    private long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
