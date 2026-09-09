package com.lifehub.domain.ai;

import java.util.List;

/**
 * Port that turns raw model text into validated domain objects.
 *
 * <p>Owns steps 4 and 5 of 04-ARCHITECTURE.md 7: strip the markdown fence and stray characters,
 * parse, then check the result against the agreed schema. Everything it returns is already known to
 * be well formed, so callers never guard against half-filled drafts.
 *
 * <p>Implemented by {@code infrastructure.ai.ParseResponseReader}.
 */
public interface AiResponseReader {

    /**
     * Reads a natural language parse answer.
     *
     * @param rawText the model's completion, fence and all
     * @param context the user's own categories, wallets and projects, used to resolve the names the
     *     model returned into real ids
     * @throws AiInvalidResponseException when the text is not JSON, or does not match the schema
     */
    ParseResult readParse(String rawText, ParseContext context);

    /**
     * Reads a category suggestion answer, keeping at most three entries (UC-10 step 4).
     *
     * @throws AiInvalidResponseException when the text is not JSON, or does not match the schema
     */
    List<CategorySuggestion> readCategorySuggestions(String rawText, ParseContext context);
}
