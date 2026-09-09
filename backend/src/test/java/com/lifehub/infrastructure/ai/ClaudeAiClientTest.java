package com.lifehub.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.MessageCreateParams;
import com.lifehub.domain.ai.AiClient.AiRequest;
import com.lifehub.domain.ai.AiErrorCode;
import com.lifehub.domain.ai.AiUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How a provider failure becomes an {@link AiErrorCode}.
 *
 * <p>This mapping is what decides three separate things: the code stored in {@code ai_parse_log},
 * the warning the offline banner shows, and whether the user is told to check their key or to try
 * again later. Getting it wrong is silent - the request still falls back and still succeeds - so it
 * needs asserting rather than reading.
 *
 * <p>The SDK client is substituted through {@code buildClient}, so nothing here touches the network.
 */
class ClaudeAiClientTest {

    private static final AiProperties CONFIGURED =
            new AiProperties("sk-ant-test", "claude-opus-5", Duration.ofSeconds(5), 1024);

    private static final AiRequest REQUEST =
            new AiRequest(null, "system", "user", Duration.ofSeconds(5), 256);

    @Test
    @DisplayName("401 từ nhà cung cấp cho mã AUTH")
    void mapsUnauthorizedToAuth() {
        AiUnavailableException thrown = callThrowing(
                UnauthorizedException.builder().headers(Headers.builder().build()).body(JsonValue.from(null)).build());

        assertThat(thrown.getErrorCode()).isEqualTo(AiErrorCode.AUTH);
        assertThat(thrown).hasMessageContaining("API key không hợp lệ");
    }

    @Test
    @DisplayName("429 cho mã RATE_LIMIT")
    void mapsRateLimitToRateLimit() {
        AiUnavailableException thrown = callThrowing(
                RateLimitException.builder().headers(Headers.builder().build()).body(JsonValue.from(null)).build());

        assertThat(thrown.getErrorCode()).isEqualTo(AiErrorCode.RATE_LIMIT);
        assertThat(thrown).hasMessageContaining("hạn mức");
    }

    @Test
    @DisplayName("Lỗi mạng hoặc quá hạn chờ cho mã TIMEOUT")
    void mapsIoFailureToTimeout() {
        AiUnavailableException thrown =
                callThrowing(new AnthropicIoException("timeout", new java.io.IOException("read timed out")));

        assertThat(thrown.getErrorCode()).isEqualTo(AiErrorCode.TIMEOUT);
        assertThat(thrown).hasMessageContaining("Không kết nối được");
    }

    @Test
    @DisplayName("Lỗi lạ ngoài danh mục cho mã UNKNOWN, không thoát ra ngoài dạng thô")
    void mapsAnythingElseToUnknown() {
        AiUnavailableException thrown = callThrowing(new IllegalStateException("bất ngờ"));

        assertThat(thrown.getErrorCode()).isEqualTo(AiErrorCode.UNKNOWN);
    }

    @Test
    @DisplayName("Chưa có API key thì trả AUTH ngay, không gọi ra ngoài")
    void refusesWithoutAnApiKey() {
        ClaudeAiClient client = new ClaudeAiClient(
                new AiProperties("", "claude-opus-5", Duration.ofSeconds(5), 1024)) {
            @Override
            AnthropicClient buildClient(Duration timeout) {
                throw new AssertionError("Không được dựng client khi chưa có API key");
            }
        };

        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.complete(REQUEST))
                .isInstanceOf(AiUnavailableException.class)
                .satisfies(error -> assertThat(((AiUnavailableException) error).getErrorCode())
                        .isEqualTo(AiErrorCode.AUTH));
    }

    @Test
    @DisplayName("Thông báo lỗi không bao giờ chứa API key")
    void neverEchoesTheApiKeyInAMessage() {
        AiUnavailableException thrown = callThrowing(
                UnauthorizedException.builder().headers(Headers.builder().build()).body(JsonValue.from(null)).build());

        assertThat(thrown.getMessage()).doesNotContain("sk-ant-test");
    }

    @Test
    @DisplayName("defaultModel trả model cấu hình sẵn cho màn hình trạng thái")
    void reportsTheConfiguredDefaultModel() {
        assertThat(clientThrowing(new IllegalStateException("x")).defaultModel())
                .isEqualTo("claude-opus-5");
    }

    /** Calls {@code complete} against a client whose {@code messages().create} throws. */
    private AiUnavailableException callThrowing(RuntimeException failure) {
        ClaudeAiClient client = clientThrowing(failure);

        try {
            client.complete(REQUEST);
        } catch (AiUnavailableException e) {
            return e;
        }
        throw new AssertionError("Phải ném AiUnavailableException, nhưng không có lỗi nào");
    }

    private ClaudeAiClient clientThrowing(RuntimeException failure) {
        AnthropicClient stub = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
        when(stub.messages().create(any(MessageCreateParams.class))).thenThrow(failure);

        return new ClaudeAiClient(CONFIGURED) {
            @Override
            AnthropicClient buildClient(Duration timeout) {
                return stub;
            }
        };
    }
}
