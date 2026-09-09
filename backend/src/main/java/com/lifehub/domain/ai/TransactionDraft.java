package com.lifehub.domain.ai;

import com.lifehub.domain.finance.TransactionType;
import java.time.Instant;
import java.util.Map;

/**
 * A transaction the user probably meant (FR-AI-03), still unsaved.
 *
 * <p>{@code amount} is a plain {@code Long} rather than a {@code Money}: a draft may legitimately
 * carry no amount at all when the sentence did not mention one, and {@code Money} has no null. The
 * value becomes a {@code Money} at the point the user confirms and {@code TransactionService}
 * takes over.
 *
 * <p>Ids are resolved here, on the backend, by matching the name the model returned against the
 * user's own categories and wallets. The model never sees an id and could not invent a valid one.
 *
 * @param fieldConfidence per field certainty; anything below 0.6 is blanked and highlighted by the
 *     UI rather than silently accepted (UC-09 alternate flow 10a)
 */
public record TransactionDraft(
        TransactionType type,
        Long amount,
        String categoryId,
        String categoryName,
        String walletId,
        String walletName,
        String note,
        Instant occurredAt,
        Map<String, Double> fieldConfidence) {

    public TransactionDraft {
        fieldConfidence = fieldConfidence == null ? Map.of() : Map.copyOf(fieldConfidence);
    }
}
