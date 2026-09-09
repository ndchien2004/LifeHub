package com.lifehub.infrastructure.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI settings that are not the user's to change from inside the app.
 *
 * <p>The key arrives from the environment because that is the only place it can arrive from: it is
 * kept in the operating system credential store by the Electron shell and handed to the JVM at spawn
 * time (03-DATA-MODEL.md 2.11). It is never written to {@code setting}, to the database, or to a log
 * line - NFR-SEC-01, and Phase 4's acceptance criteria grep {@code data/} and {@code logs/} to prove
 * it.
 *
 * <p>Which model to call is a user choice and lives in {@code setting/ai.model}; {@code defaultModel}
 * here is only what to use before they have chosen.
 *
 * @param apiKey provider credential, blank when the user has not configured one yet
 * @param defaultModel model id used when {@code setting/ai.model} is unset
 * @param timeout deadline for one interactive call (04-ARCHITECTURE.md 7, UC-09 exception E1)
 * @param maxTokens ceiling on the answer; the JSON schema is small, prose is never wanted
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String apiKey, String defaultModel, Duration timeout, int maxTokens) {

    public static final String FALLBACK_MODEL = "claude-opus-5";

    public AiProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        defaultModel = isBlank(defaultModel) ? FALLBACK_MODEL : defaultModel.trim();
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        maxTokens = maxTokens <= 0 ? 1024 : maxTokens;
    }

    public boolean hasApiKey() {
        return !apiKey.isBlank();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
