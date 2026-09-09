package com.lifehub.application.ai;

import com.lifehub.application.system.SettingService;
import com.lifehub.domain.ai.AiClient;
import org.springframework.stereotype.Service;

/**
 * The two AI choices that belong to the user, read from {@code setting} (03-DATA-MODEL.md 2.11).
 *
 * <p>Kept apart from {@code AiProperties}, which holds what the environment supplies. The split
 * matters: the user can switch AI off or pick a different model from the Settings screen at any
 * time, while the API key can only change by way of the operating system credential store and a
 * backend restart.
 */
@Service
public class AiSettings {

    public static final String ENABLED_KEY = "ai.enabled";
    public static final String MODEL_KEY = "ai.model";

    private final SettingService settingService;
    private final AiClient aiClient;

    public AiSettings(SettingService settingService, AiClient aiClient) {
        this.settingService = settingService;
        this.aiClient = aiClient;
    }

    /** Whether the user has left AI switched on (FR-AI-12). Defaults to on. */
    public boolean isEnabled() {
        return !"false".equalsIgnoreCase(settingService.get(ENABLED_KEY, "true"));
    }

    /** Whether an API key reached the backend (FR-AI-09). */
    public boolean isConfigured() {
        return aiClient.isConfigured();
    }

    /**
     * Whether a call would actually be attempted.
     *
     * <p>Both halves have to hold. Either one missing routes the request to the rule based parser,
     * which is a normal state and not an error (SD-02, first alt branch).
     */
    public boolean isUsable() {
        return isEnabled() && isConfigured();
    }

    /** The model the user picked, or the shipped default when they have not picked one. */
    public String model() {
        String configured = settingService.get(MODEL_KEY, null);
        return configured == null || configured.isBlank() ? aiClient.defaultModel() : configured;
    }
}
