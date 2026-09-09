package com.lifehub.domain.ai;

/**
 * Port for the offline parser used whenever the AI path is unavailable or switched off (FR-AI-08).
 *
 * <p>It must never throw: this is the branch the application falls back to when something has
 * already gone wrong, and a failure here would turn a degraded feature into a broken one. A sentence
 * it cannot make sense of yields {@link ParseResult#unknown}, which the UI already handles.
 *
 * <p>Implemented by {@code infrastructure.ai.RuleBasedParser} against the specification table in
 * 04-ARCHITECTURE.md 7.
 */
public interface FallbackParser {

    ParseResult parse(String text, ParseContext context);
}
