package com.lifehub.domain.ai;

import java.time.Duration;

/**
 * Transport port to the language model (04-ARCHITECTURE.md 7).
 *
 * <p>Deliberately narrow: text in, text out, plus the counters {@code ai_parse_log} needs. Prompt
 * assembly, sanitising and schema validation all sit outside it, which is what lets a test swap in a
 * client that returns a fixed string and still exercise every branch of SD-02 (T4-17 to T4-19).
 *
 * <p>The implementation lives in {@code infrastructure.ai}; the domain never learns which provider
 * is on the other end.
 */
public interface AiClient {

    /** Whether an API key is present. False means every call would fail with {@code AUTH}. */
    boolean isConfigured();

    /** The model used when a request does not name one. Shown by the status endpoint. */
    String defaultModel();

    /**
     * Sends one prompt and waits for the whole answer.
     *
     * @throws AiUnavailableException on timeout, network failure, a rejected key, or throttling
     */
    AiCompletion complete(AiRequest request);

    /**
     * One request to the model.
     *
     * @param model the model to call, or null to use {@link #defaultModel()}
     * @param systemPrompt instructions that frame the task, constant per template
     * @param userPrompt the sentence plus the user's own context
     * @param timeout hard deadline; 5 seconds for interactive parsing (04-ARCHITECTURE.md 7)
     * @param maxTokens ceiling on the answer, sized to the JSON schema rather than to prose
     */
    record AiRequest(
            String model, String systemPrompt, String userPrompt, Duration timeout, int maxTokens) {
    }

    /**
     * One answer from the model.
     *
     * @param text the raw completion, still to be sanitised
     * @param inputTokens tokens billed for the prompt, or 0 when the provider did not report them
     * @param outputTokens tokens billed for the answer
     * @param model the model that actually served the request, which may differ from the one asked
     *     for if the provider aliased it
     */
    record AiCompletion(String text, int inputTokens, int outputTokens, String model) {
    }
}
