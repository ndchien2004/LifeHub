package com.lifehub.application.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lifehub.domain.ai.AiClient;
import com.lifehub.domain.ai.AiClient.AiCompletion;
import com.lifehub.domain.ai.AiErrorCode;
import com.lifehub.domain.ai.AiRequestType;
import com.lifehub.domain.ai.AiResponseReader;
import com.lifehub.domain.ai.AiUnavailableException;
import com.lifehub.domain.ai.CategorySuggestion;
import com.lifehub.domain.ai.ParseContext;
import com.lifehub.domain.ai.ParseContext.CategoryOption;
import com.lifehub.domain.ai.ParseContext.RecentTransaction;
import com.lifehub.domain.ai.PromptTemplates;
import com.lifehub.domain.ai.PromptTemplates.Prompt;
import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.TransactionType;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UC-10 — category suggestions, including the two exception flows the use case names. */
class CategorySuggestServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AiSettings settings = mock(AiSettings.class);
    private final AiContextProvider contextProvider = mock(AiContextProvider.class);
    private final PromptTemplates prompts = mock(PromptTemplates.class);
    private final AiClient aiClient = mock(AiClient.class);
    private final AiResponseReader reader = mock(AiResponseReader.class);
    private final AiLogService logService = mock(AiLogService.class);

    private final CategorySuggestService service = new CategorySuggestService(
            settings, contextProvider, prompts, aiClient, reader, logService);

    @BeforeEach
    void setUp() {
        when(prompts.build(anyString(), any())).thenReturn(new Prompt("system", "user"));
        when(settings.model()).thenReturn("claude-opus-5");
    }

    @Test
    @DisplayName("UC-10 E2 — ghi chú trùng khớp lịch sử thì dùng luôn, không gọi AI")
    void reusesTheUserOwnHistoryInsteadOfCallingAi() {
        when(contextProvider.load()).thenReturn(contextWithHistory());
        when(settings.isUsable()).thenReturn(true);

        List<CategorySuggestion> suggestions = service.suggest("Trà sữa", CategoryType.EXPENSE);

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).categoryId()).isEqualTo("cat-coffee");
        assertThat(suggestions.get(0).confidence())
                .as("Chính người dùng đã phân loại như vậy, nên độ tin cậy cao hơn mọi phỏng đoán")
                .isGreaterThan(0.9);
        verifyNoInteractions(aiClient, reader);
    }

    @Test
    @DisplayName("Khớp lịch sử bỏ qua dấu và chữ hoa thường")
    void matchesHistoryWithoutDiacritics() {
        when(contextProvider.load()).thenReturn(contextWithHistory());
        when(settings.isUsable()).thenReturn(true);

        assertThat(service.suggest("tra sua", CategoryType.EXPENSE)).hasSize(1);
        verifyNoInteractions(aiClient);
    }

    @Test
    @DisplayName("Không khớp lịch sử thì hỏi AI và ghi log")
    void asksTheModelWhenHistoryDoesNotAnswer() {
        when(contextProvider.load()).thenReturn(contextWithHistory());
        when(settings.isUsable()).thenReturn(true);
        when(aiClient.complete(any()))
                .thenReturn(new AiCompletion("{}", 90, 20, "claude-opus-5"));
        when(reader.readCategorySuggestions(anyString(), any()))
                .thenReturn(List.of(new CategorySuggestion("cat-food", "Ăn uống", 0.8)));

        List<CategorySuggestion> suggestions = service.suggest("bún bò Huế", CategoryType.EXPENSE);

        assertThat(suggestions).hasSize(1);
        verify(logService).recordSuccess(
                eq(AiRequestType.CATEGORY_SUGGEST), eq("bún bò Huế"), eq(null),
                anyString(), anyString(), anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("UC-10 E1 — AI lỗi thì im lặng trả về rỗng, không ném lỗi lên UI")
    void staysSilentWhenTheModelIsUnavailable() {
        when(contextProvider.load()).thenReturn(contextWithHistory());
        when(settings.isUsable()).thenReturn(true);
        when(aiClient.complete(any()))
                .thenThrow(new AiUnavailableException(AiErrorCode.TIMEOUT, "hết thời gian chờ"));

        assertThat(service.suggest("bún bò Huế", CategoryType.EXPENSE)).isEmpty();
        verify(logService).recordFailure(
                eq(AiRequestType.CATEGORY_SUGGEST), eq("bún bò Huế"), eq(AiErrorCode.TIMEOUT),
                anyString(), anyLong());
    }

    @Test
    @DisplayName("Tắt AI và không có lịch sử thì không gợi ý gì, cũng không gọi API")
    void suggestsNothingWhenAiIsOff() {
        when(contextProvider.load()).thenReturn(contextWithHistory());
        when(settings.isUsable()).thenReturn(false);

        assertThat(service.suggest("bún bò Huế", CategoryType.EXPENSE)).isEmpty();
        verify(aiClient, never()).complete(any());
    }

    @Test
    @DisplayName("Ghi chú rỗng không kích hoạt bất cứ thứ gì")
    void ignoresBlankNotes() {
        assertThat(service.suggest("   ", CategoryType.EXPENSE)).isEmpty();
        assertThat(service.suggest(null, CategoryType.EXPENSE)).isEmpty();
        verifyNoInteractions(contextProvider, aiClient, logService);
    }

    private static ParseContext contextWithHistory() {
        return new ParseContext(
                Instant.parse("2026-09-11T03:00:00Z"),
                ZONE,
                List.of(
                        new CategoryOption("cat-food", "Ăn uống", null, CategoryType.EXPENSE),
                        new CategoryOption("cat-coffee", "Cà phê", "Ăn uống", CategoryType.EXPENSE)),
                List.of(),
                List.of(),
                List.of(
                        new RecentTransaction(
                                "Trà sữa", "Ăn uống › Cà phê", TransactionType.EXPENSE, 45_000L),
                        new RecentTransaction("Cơm trưa", "Ăn uống", TransactionType.EXPENSE, 50_000L)));
    }
}
