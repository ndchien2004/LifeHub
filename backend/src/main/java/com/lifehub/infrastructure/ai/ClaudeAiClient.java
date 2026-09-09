package com.lifehub.infrastructure.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.lifehub.domain.ai.AiClient;
import com.lifehub.domain.ai.AiErrorCode;
import com.lifehub.domain.ai.AiUnavailableException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Calls the Claude Messages API (04-ARCHITECTURE.md 7).
 *
 * <p>Everything above this class works in terms of prompts and validated drafts; this is the only
 * place that knows a provider exists. It does no sanitising and no schema checking - those belong to
 * {@code ParseResponseReader}, so a test can drive the whole of SD-02 with a client that returns a
 * fixed string.
 *
 * <p>Effort is pinned to {@code LOW}. Turning a short Vietnamese sentence into six fields is not a
 * reasoning problem, and the interactive budget is five seconds end to end - a deeper thinking pass
 * would spend the whole budget and land the user in the offline fallback every time.
 *
 * <p>The API key never leaves this object: it is read from {@link AiProperties}, handed to the SDK,
 * and never logged, echoed in an exception message, or written to the database.
 */
@Component
public class ClaudeAiClient implements AiClient {

    private final AiProperties properties;

    /**
     * One SDK client per timeout, built on first use.
     *
     * <p>The SDK sets its deadline at construction, and each client owns a connection pool worth
     * reusing across calls. There are at most a handful of distinct timeouts in the application, so
     * the map stays tiny.
     */
    private final Map<Duration, AnthropicClient> clients = new ConcurrentHashMap<>();

    public ClaudeAiClient(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return properties.hasApiKey();
    }

    @Override
    public String defaultModel() {
        return properties.defaultModel();
    }

    @Override
    public AiCompletion complete(AiRequest request) {
        if (!isConfigured()) {
            throw new AiUnavailableException(
                    AiErrorCode.AUTH, "Chưa cấu hình API key cho tính năng AI");
        }

        String model = request.model() == null || request.model().isBlank()
                ? properties.defaultModel()
                : request.model();

        int maxTokens = request.maxTokens() > 0 ? request.maxTokens() : properties.maxTokens();

        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .addUserMessage(request.userPrompt());

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            params.system(request.systemPrompt());
        }

        try {
            Message message = client(request.timeout()).messages().create(params.build());
            return new AiCompletion(
                    textOf(message),
                    (int) message.usage().inputTokens(),
                    (int) message.usage().outputTokens(),
                    message.model().toString());
        } catch (UnauthorizedException e) {
            throw new AiUnavailableException(
                    AiErrorCode.AUTH, "API key không hợp lệ hoặc đã bị thu hồi", e);
        } catch (RateLimitException e) {
            throw new AiUnavailableException(
                    AiErrorCode.RATE_LIMIT, "Đã vượt hạn mức gọi AI, thử lại sau ít phút", e);
        } catch (AnthropicIoException e) {
            // Covers both a socket that never answered and one that answered too late.
            throw new AiUnavailableException(
                    AiErrorCode.TIMEOUT, "Không kết nối được tới dịch vụ AI", e);
        } catch (AnthropicServiceException e) {
            throw new AiUnavailableException(
                    AiErrorCode.UNKNOWN, "Dịch vụ AI trả về lỗi, đã chuyển sang chế độ ngoại tuyến", e);
        } catch (RuntimeException e) {
            // A provider fault must never become a 500 for the user: the caller falls back.
            throw new AiUnavailableException(
                    AiErrorCode.UNKNOWN, "Không gọi được dịch vụ AI", e);
        }
    }

    /** Concatenates every text block; tool and thinking blocks are not requested and not expected. */
    private String textOf(Message message) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : message.content()) {
            block.text().ifPresent(t -> text.append(t.text()));
        }
        return text.toString();
    }

    private AnthropicClient client(Duration timeout) {
        Duration effective = timeout == null ? properties.timeout() : timeout;
        return clients.computeIfAbsent(effective, this::buildClient);
    }

    /**
     * Builds the SDK client for one timeout.
     *
     * <p>Package private rather than private so a test can substitute a client that throws, which is
     * the only way to exercise the provider-error mapping below without a network round trip.
     */
    AnthropicClient buildClient(Duration timeout) {
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .timeout(timeout)
                // Retrying is the caller's decision: SD-02 allows exactly one retry, and the SDK
                // default of two would silently spend the five second budget three times over.
                .maxRetries(0)
                .build();
    }
}
