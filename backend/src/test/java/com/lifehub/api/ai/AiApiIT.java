package com.lifehub.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifehub.application.system.SettingService;
import com.lifehub.domain.ai.AiParseLogRepository;
import com.lifehub.domain.common.PageRequest;
import com.lifehub.domain.finance.TransactionFilter;
import com.lifehub.domain.finance.TransactionRepository;
import com.lifehub.domain.task.TaskRepository;
import com.lifehub.support.ApiIntegrationTest;
import com.lifehub.support.TestDatabase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T4-20 and T4-23 end to end, plus the acceptance criterion the phase plan puts in bold.
 *
 * <p>No API key is configured in the test profile, so every call here takes the offline branch of
 * SD-02. That is deliberate: it is the branch a user hits when the network is down, and it is the
 * one that must never throw.
 *
 * <p>T4-22 - grepping {@code data/} and {@code logs/} for the key - is covered by
 * {@link ApiKeyLeakIT}, which needs a key actually configured and so cannot share this class's
 * context.
 */
class AiApiIT extends ApiIntegrationTest {

    private static final String DATABASE_URL = TestDatabase.freshUrl();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    private AiParseLogRepository logRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private SettingService settingService;

    @BeforeEach
    void resetAiSetting() {
        settingService.put("ai.enabled", "true");
    }

    @Test
    @DisplayName("POST /ai/parse trả giao dịch điền sẵn, đánh dấu nguồn RULE khi chưa có API key")
    void parsesATransactionOffline() throws Exception {
        mockMvc.perform(authed(post("/api/v1/ai/parse")).content(parseBody("ăn trưa cơm gà 45k")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.intent").value("TRANSACTION"))
                .andExpect(jsonPath("$.data.source").value("RULE"))
                .andExpect(jsonPath("$.data.transaction.amount").value(45000))
                .andExpect(jsonPath("$.data.transaction.type").value("EXPENSE"))
                // value(nullValue()) rather than doesNotExist(): the latter also passes when the
                // member is absent, and 06-API-SPEC.md §8 documents the shape as an explicit
                // "task": null. An omitted member would break a client reading result.task.
                .andExpect(jsonPath("$.data.task").value(nullValue()))
                .andExpect(jsonPath("$.data.event").value(nullValue()));
    }

    @Test
    @DisplayName("POST /ai/parse nhận diện được công việc")
    void parsesATask() throws Exception {
        mockMvc.perform(authed(post("/api/v1/ai/parse")).content(parseBody("mua quà sinh nhật mẹ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("TASK"))
                .andExpect(jsonPath("$.data.task.title").value("mua quà sinh nhật mẹ"))
                .andExpect(jsonPath("$.data.transaction").value(nullValue()))
                .andExpect(jsonPath("$.data.event").value(nullValue()));
    }

    @Test
    @DisplayName("**Bấm Esc ở form kết quả → không có bản ghi nào được tạo trong DB**")
    void parsingAloneWritesNoBusinessData() throws Exception {
        long transactionsBefore = countTransactions();
        long tasksBefore = countTasks();

        mockMvc.perform(authed(post("/api/v1/ai/parse")).content(parseBody("ăn trưa cơm gà 45k")))
                .andExpect(status().isOk());
        mockMvc.perform(authed(post("/api/v1/ai/parse")).content(parseBody("mua quà sinh nhật mẹ")))
                .andExpect(status().isOk());
        mockMvc.perform(authed(post("/api/v1/ai/parse")).content(parseBody("họp 2h chiều mai")))
                .andExpect(status().isOk());

        // Nothing confirmed the drafts, so nothing may exist. Queried straight from the repository
        // rather than through an endpoint, so a filtered view cannot hide a row.
        assertThat(countTransactions())
                .as("Phân tích câu không được tạo giao dịch nào")
                .isEqualTo(transactionsBefore);
        assertThat(countTasks()).as("Phân tích câu không được tạo task nào").isEqualTo(tasksBefore);
    }

    @Test
    @DisplayName("T4-20 — mỗi lần gọi /ai/parse ghi đúng một bản ghi ai_parse_log")
    void writesExactlyOneLogRowPerCall() throws Exception {
        long before = countLogs();

        mockMvc.perform(authed(post("/api/v1/ai/parse")).content(parseBody("cà phê 45k")))
                .andExpect(status().isOk());

        assertThat(countLogs()).isEqualTo(before + 1);

        mockMvc.perform(authed(post("/api/v1/ai/parse")).content(parseBody("mua sách")))
                .andExpect(status().isOk());

        assertThat(countLogs()).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("T4-23 — tắt AI thì vẫn parse được bằng bộ luật, không lỗi")
    void stillParsesWhenAiIsDisabled() throws Exception {
        settingService.put("ai.enabled", "false");

        mockMvc.perform(authed(post("/api/v1/ai/parse")).content(parseBody("cà phê 45k")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("RULE"))
                .andExpect(jsonPath("$.data.transaction.amount").value(45000));

        mockMvc.perform(authed(get("/api/v1/ai/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    @DisplayName("Câu rỗng bị chặn bằng VALIDATION_ERROR, không phải 500")
    void rejectsEmptyInput() throws Exception {
        mockMvc.perform(authed(post("/api/v1/ai/parse")).content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /ai/status cho biết chưa cấu hình API key")
    void reportsUnconfiguredStatus() throws Exception {
        mockMvc.perform(authed(get("/api/v1/ai/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.model").isNotEmpty());
    }

    @Test
    @DisplayName("POST /ai/test-connection khi chưa có key trả 428 AI_NOT_CONFIGURED, không stack trace")
    void reportsMissingKeyClearly() throws Exception {
        String body = body(mockMvc.perform(authed(post("/api/v1/ai/test-connection")))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.error.code").value("AI_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.error.traceId").isNotEmpty())
                .andReturn());

        assertThat(body)
                .as("NFR-USE-03: không bao giờ lộ stack trace ra frontend")
                .doesNotContain("Exception")
                .doesNotContain("at com.lifehub");
    }

    @Test
    @DisplayName("POST /ai/suggest-category trả mảng rỗng khi không có gì để gợi ý")
    void suggestsNothingWithoutHistoryOrAi() throws Exception {
        mockMvc.perform(authed(post("/api/v1/ai/suggest-category"))
                        .content("{\"note\":\"trà sữa gongcha\",\"type\":\"EXPENSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestions").isArray())
                .andExpect(jsonPath("$.data.suggestions").isEmpty());
    }

    @Test
    @DisplayName("GET /ai/logs trả về nhật ký, mới nhất trước, và không chứa API key")
    void listsRecentLogs() throws Exception {
        mockMvc.perform(authed(post("/api/v1/ai/parse")).content(parseBody("cà phê 45k")))
                .andExpect(status().isOk());

        String body = body(mockMvc.perform(authed(get("/api/v1/ai/logs?page=0&size=10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].requestType").value("NL_PARSE"))
                .andExpect(jsonPath("$.data.items[0].inputText").value("cà phê 45k"))
                .andReturn());

        assertThat(body).doesNotContain("apiKey").doesNotContain("sk-ant");
    }

    @Test
    @DisplayName("Gọi /ai/parse thiếu token vẫn bị chặn như mọi endpoint khác")
    void requiresTheAppToken() throws Exception {
        mockMvc.perform(post("/api/v1/ai/parse")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(parseBody("cà phê 45k")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private String parseBody(String text) throws Exception {
        return json(Map.of("text", text));
    }

    private long countLogs() {
        return logRepository.findRecent(PageRequest.of(0, 1)).totalItems();
    }

    private long countTransactions() {
        return transactionRepository.findAll(TransactionFilter.empty()).size();
    }

    private long countTasks() {
        return taskRepository.search(
                        com.lifehub.domain.task.TaskFilter.none(), PageRequest.of(0, 1))
                .totalItems();
    }
}
