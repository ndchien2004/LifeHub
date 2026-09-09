package com.lifehub.domain.ai;

import java.util.Map;

/**
 * Port for loading a prompt template and substituting its variables.
 *
 * <p>Templates are files under {@code resources/prompts/} rather than string literals in Java, so
 * the wording can be tuned without touching parsing logic and a diff of a prompt change reads as a
 * prompt change. Implemented by {@code infrastructure.ai.PromptBuilder}.
 */
public interface PromptTemplates {

    /**
     * Loads {@code name} and replaces every {@code {{key}}} placeholder with its value.
     *
     * @param name template file name without extension, for example {@code nl-parse}
     * @param variables values to substitute; a placeholder with no value becomes an empty string
     */
    Prompt build(String name, Map<String, String> variables);

    /**
     * A template split into the two messages a chat model expects.
     *
     * @param system the standing instructions - the schema, the rules, the output format
     * @param user this particular request - the sentence plus the user's own context
     */
    record Prompt(String system, String user) {
    }
}
