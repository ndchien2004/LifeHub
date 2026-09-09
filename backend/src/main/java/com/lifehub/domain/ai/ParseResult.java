package com.lifehub.domain.ai;

/**
 * What a natural language sentence was understood to mean (SD-02, step 8 of 04-ARCHITECTURE.md 7).
 *
 * <p>Exactly one draft is populated - the one matching {@link #intent} - and the others are null.
 * A draft is a proposal, never a record: nothing here has an id, nothing has been written, and the
 * only way it becomes data is the user confirming the prefilled form and the frontend issuing its
 * own POST (AGENTS.md 3.4 rule 1).
 *
 * @param confidence how sure the parser is about the intent as a whole, 0.0 to 1.0
 * @param source which parser produced this, so the UI can warn when it is the offline fallback
 * @param warning error code to surface alongside a fallback result, or null
 */
public record ParseResult(
        ParseIntent intent,
        double confidence,
        ParseSource source,
        TransactionDraft transaction,
        TaskDraft task,
        EventDraft event,
        AiErrorCode warning) {

    public static ParseResult unknown(ParseSource source) {
        return new ParseResult(ParseIntent.UNKNOWN, 0.0, source, null, null, null, null);
    }

    public static ParseResult of(TransactionDraft draft, double confidence, ParseSource source) {
        return new ParseResult(ParseIntent.TRANSACTION, confidence, source, draft, null, null, null);
    }

    public static ParseResult of(TaskDraft draft, double confidence, ParseSource source) {
        return new ParseResult(ParseIntent.TASK, confidence, source, null, draft, null, null);
    }

    public static ParseResult of(EventDraft draft, double confidence, ParseSource source) {
        return new ParseResult(ParseIntent.EVENT, confidence, source, null, null, draft, null);
    }

    /** The same result tagged with the reason the AI path was not used. */
    public ParseResult withWarning(AiErrorCode code) {
        return new ParseResult(intent, confidence, source, transaction, task, event, code);
    }
}
