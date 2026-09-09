package com.lifehub.application.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lifehub.domain.ai.AiClient;
import com.lifehub.domain.ai.AiClient.AiCompletion;
import com.lifehub.domain.ai.AiClient.AiRequest;
import com.lifehub.domain.ai.AiErrorCode;
import com.lifehub.domain.ai.AiInvalidResponseException;
import com.lifehub.domain.ai.AiRequestType;
import com.lifehub.domain.ai.AiResponseReader;
import com.lifehub.domain.ai.AiUnavailableException;
import com.lifehub.domain.ai.FallbackParser;
import com.lifehub.domain.ai.ParseContext;
import com.lifehub.domain.ai.ParseIntent;
import com.lifehub.domain.ai.ParseResult;
import com.lifehub.domain.ai.ParseSource;
import com.lifehub.domain.ai.PromptTemplates;
import com.lifehub.domain.ai.PromptTemplates.Prompt;
import com.lifehub.domain.ai.TaskDraft;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.task.Priority;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * T4-17, T4-18, T4-19, T4-23 — every branch of SD-02, driven through a mocked {@code AiClient}.
 *
 * <p>Each case asserts two things: what the caller gets back, and that exactly one row went to the
 * log. The second half is T4-20's requirement at the unit level - a call that is not recorded cannot
 * be debugged later.
 */
class NlParseServiceTest {

    private static final String INPUT = "mua quà sinh nhật mẹ";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AiSettings settings = mock(AiSettings.class);
    private final AiContextProvider contextProvider = mock(AiContextProvider.class);
    private final PromptTemplates prompts = mock(PromptTemplates.class);
    private final AiClient aiClient = mock(AiClient.class);
    private final AiResponseReader reader = mock(AiResponseReader.class);
    private final FallbackParser fallbackParser = mock(FallbackParser.class);
    private final AiLogService logService = mock(AiLogService.class);

    private final NlParseService service = new NlParseService(
            settings, contextProvider, prompts, aiClient, reader, fallbackParser, logService);

    @BeforeEach
    void setUp() {
        when(contextProvider.load()).thenReturn(ParseContext.empty(Instant.now(), ZONE));
        when(prompts.build(anyString(), any())).thenReturn(new Prompt("system", "user"));
        when(settings.model()).thenReturn("claude-opus-5");
        when(fallbackParser.parse(anyString(), any())).thenReturn(ruleResult());
    }

    @Test
    @DisplayName("Đường thành công — trả kết quả của AI và ghi đúng một bản ghi log")
    void returnsAiResultOnTheHappyPath() {
        when(settings.isUsable()).thenReturn(true);
        when(aiClient.complete(any())).thenReturn(completion("{}"));
        when(reader.readParse(anyString(), any())).thenReturn(aiResult());

        ParseResult result = service.parse(INPUT);

        assertThat(result.source()).isEqualTo(ParseSource.AI);
        assertThat(result.warning()).isNull();
        verify(aiClient, times(1)).complete(any());
        verify(logService, times(1)).recordSuccess(
                eq(AiRequestType.NL_PARSE), eq(INPUT), eq(ParseIntent.TASK),
                anyString(), anyString(), anyLong(), anyInt(), anyInt());
        verifyNoInteractions(fallbackParser);
    }

    @Test
    @DisplayName("T4-17 — client timeout thì fallback rule-based, source = RULE")
    void fallsBackOnTimeout() {
        when(settings.isUsable()).thenReturn(true);
        when(aiClient.complete(any()))
                .thenThrow(new AiUnavailableException(AiErrorCode.TIMEOUT, "hết thời gian chờ"));

        ParseResult result = service.parse(INPUT);

        assertThat(result.source()).isEqualTo(ParseSource.RULE);
        assertThat(result.warning()).isEqualTo(AiErrorCode.TIMEOUT);
        verify(aiClient, times(1)).complete(any());
        verify(logService).recordFailure(
                eq(AiRequestType.NL_PARSE), eq(INPUT), eq(AiErrorCode.TIMEOUT), anyString(), anyLong());
    }

    @Test
    @DisplayName("T4-18 — JSON sai schema thì thử lại đúng một lần rồi mới fallback")
    void retriesOnceThenFallsBackOnInvalidSchema() {
        when(settings.isUsable()).thenReturn(true);
        when(aiClient.complete(any())).thenReturn(completion("không phải JSON"));
        when(reader.readParse(anyString(), any()))
                .thenThrow(new AiInvalidResponseException("sai schema"));

        ParseResult result = service.parse(INPUT);

        assertThat(result.source()).isEqualTo(ParseSource.RULE);
        assertThat(result.warning()).isEqualTo(AiErrorCode.INVALID_JSON);
        verify(aiClient, times(2))
                .complete(any());
        verify(logService).recordFailure(
                eq(AiRequestType.NL_PARSE), eq(INPUT), eq(AiErrorCode.INVALID_JSON),
                anyString(), anyLong());
    }

    @Test
    @DisplayName("T4-18 — lần thử lại nhấn mạnh 'chỉ trả JSON' trong prompt")
    void secondAttemptAsksForJsonOnly() {
        when(settings.isUsable()).thenReturn(true);
        when(aiClient.complete(any())).thenReturn(completion("rác"));
        when(reader.readParse(anyString(), any()))
                .thenThrow(new AiInvalidResponseException("sai schema"));

        service.parse(INPUT);

        ArgumentCaptor<Map<String, String>> variables = captor();
        verify(prompts, times(2)).build(eq("nl-parse"), variables.capture());

        assertThat(variables.getAllValues().get(0).get("retryHint")).isEmpty();
        assertThat(variables.getAllValues().get(1).get("retryHint"))
                .contains("CHỈ trả về JSON thuần");
    }

    @Test
    @DisplayName("T4-18 — lần thử lại thành công thì trả kết quả AI, không fallback")
    void keepsAiResultWhenRetrySucceeds() {
        when(settings.isUsable()).thenReturn(true);
        when(aiClient.complete(any())).thenReturn(completion("{}"));
        when(reader.readParse(anyString(), any()))
                .thenThrow(new AiInvalidResponseException("sai schema"))
                .thenReturn(aiResult());

        ParseResult result = service.parse(INPUT);

        assertThat(result.source()).isEqualTo(ParseSource.AI);
        verify(aiClient, times(2)).complete(any());
        verifyNoInteractions(fallbackParser);
    }

    @Test
    @DisplayName("T4-19 — 401 cho mã lỗi AUTH và fallback, không thử lại")
    void reportsAuthErrorAndFallsBack() {
        when(settings.isUsable()).thenReturn(true);
        when(aiClient.complete(any()))
                .thenThrow(new AiUnavailableException(AiErrorCode.AUTH, "API key không hợp lệ"));

        ParseResult result = service.parse(INPUT);

        assertThat(result.warning()).isEqualTo(AiErrorCode.AUTH);
        assertThat(result.source()).isEqualTo(ParseSource.RULE);
        verify(aiClient, times(1)).complete(any());
    }

    @Test
    @DisplayName("T4-23 — tắt AI thì không gọi API, dùng thẳng bộ luật, vẫn ghi log")
    void skipsTheApiEntirelyWhenAiIsDisabled() {
        when(settings.isUsable()).thenReturn(false);

        ParseResult result = service.parse(INPUT);

        assertThat(result.source()).isEqualTo(ParseSource.RULE);
        assertThat(result.warning()).as("Tắt AI không phải lỗi nên không có cảnh báo").isNull();
        verify(aiClient, never()).complete(any());
        verifyNoInteractions(prompts);
        verify(logService).recordFailure(AiRequestType.NL_PARSE, INPUT, null, null, 0L);
    }

    @Test
    @DisplayName("Model do user chọn được truyền xuống client")
    void passesTheConfiguredModel() {
        when(settings.isUsable()).thenReturn(true);
        when(settings.model()).thenReturn("claude-haiku-4-5");
        when(aiClient.complete(any())).thenReturn(completion("{}"));
        when(reader.readParse(anyString(), any())).thenReturn(aiResult());

        service.parse(INPUT);

        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(aiClient).complete(request.capture());
        assertThat(request.getValue().model()).isEqualTo("claude-haiku-4-5");
    }

    @Test
    @DisplayName("Câu rỗng bị chặn trước khi chạm tới bất kỳ thứ gì khác")
    void rejectsBlankInput() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.parse("  "))
                .isInstanceOf(ValidationException.class);

        verifyNoInteractions(aiClient, contextProvider, logService);
    }

    private static AiCompletion completion(String text) {
        return new AiCompletion(text, 120, 40, "claude-opus-5");
    }

    private static ParseResult aiResult() {
        return ParseResult.of(draft(), 0.9, ParseSource.AI);
    }

    private static ParseResult ruleResult() {
        return ParseResult.of(draft(), 0.55, ParseSource.RULE);
    }

    private static TaskDraft draft() {
        return new TaskDraft("Mua quà sinh nhật mẹ", Priority.MEDIUM, null, null, null, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, String>> captor() {
        return ArgumentCaptor.forClass(Map.class);
    }
}
