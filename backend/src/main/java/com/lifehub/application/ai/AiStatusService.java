package com.lifehub.application.ai;

import com.lifehub.domain.ai.AiClient;
import com.lifehub.domain.ai.AiClient.AiRequest;
import com.lifehub.domain.ai.AiException;
import com.lifehub.domain.ai.AiNotConfiguredException;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * Answers "is AI working?" for the Settings screen (FR-SYS-09).
 *
 * <p>{@link #status()} is a local question - it reads two settings and asks whether a key is
 * present, and costs nothing. {@link #testConnection()} is the opposite: it spends a real request,
 * so it only ever runs when the user presses the button.
 */
@Service
public class AiStatusService {

    /** Deliberately tiny. The question is whether the key works, not what the model can write. */
    private static final int PROBE_MAX_TOKENS = 16;

    private static final String PROBE_PROMPT = "Trả lời đúng một từ: OK";

    /** Longer than the interactive budget: a person waiting for a result will wait a few seconds. */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(15);

    private final AiSettings settings;
    private final AiClient aiClient;

    public AiStatusService(AiSettings settings, AiClient aiClient) {
        this.settings = settings;
        this.aiClient = aiClient;
    }

    public AiStatus status() {
        return new AiStatus(
                settings.isEnabled(),
                settings.isConfigured(),
                settings.model(),
                settings.isUsable());
    }

    /**
     * Sends one throwaway request to prove the key works (FR-AI-09, Settings "Kiểm tra kết nối").
     *
     * @return the model that answered
     * @throws AiNotConfiguredException when no key has been supplied - a 428, so the UI can point
     *     the user at the key field rather than reporting a service outage
     * @throws AiException when the provider refused or could not be reached; the message is already
     *     in Vietnamese and carries no stack trace (NFR-USE-03)
     */
    public String testConnection() {
        if (!settings.isConfigured()) {
            throw new AiNotConfiguredException(
                    "Chưa có API key. Nhập key trong màn hình Cài đặt rồi thử lại.");
        }

        String model = settings.model();
        aiClient.complete(new AiRequest(model, "", PROBE_PROMPT, PROBE_TIMEOUT, PROBE_MAX_TOKENS));
        return model;
    }

    /**
     * @param enabled the user has not switched AI off (FR-AI-12)
     * @param configured an API key reached the backend
     * @param model the model that would be called
     * @param available both of the above, which is what decides whether the palette calls out
     */
    public record AiStatus(boolean enabled, boolean configured, String model, boolean available) {
    }
}
