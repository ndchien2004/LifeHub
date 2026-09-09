package com.lifehub.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifehub.domain.ai.AiClient;
import com.lifehub.support.ApiIntegrationTest;
import com.lifehub.support.TestDatabase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T4-22 — with a key actually configured, nothing the backend writes may contain it.
 *
 * <p>The acceptance criterion is a grep of {@code data/} and {@code logs/}, so that is literally
 * what this does: drive the endpoints that see the key, then read every byte the backend wrote to
 * its data and log directories and look for it.
 *
 * <p>Scope is the backend's own artefacts. The encrypted blob Electron keeps lives outside
 * {@code data/} by design (PROGRESS.md A4-12) and is checked by hand in UAT-4-24.
 *
 * <p>The provider client is stubbed out. A test that reached the network would be slow, flaky, and
 * would prove nothing extra: every path that could write the key to disk - the config binding, the
 * log row, the settings table, the error envelope - runs regardless of what the provider answers.
 */
@Import(ApiKeyLeakIT.StubbedProvider.class)
class ApiKeyLeakIT extends ApiIntegrationTest {

    /** Distinctive enough that a match cannot be a coincidence. */
    private static final String API_KEY = "sk-ant-leak-canary-9f3b7c1e";

    private static final String DATABASE_URL = TestDatabase.freshUrl();
    private static final Path DATA_DIR = Path.of("target", "test-data");
    private static final Path LOG_DIR = Path.of("target", "test-logs");

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
        registry.add("app.ai.api-key", () -> API_KEY);
    }

    @Test
    @DisplayName("T4-22 — API key không xuất hiện dạng thường trong data/ hay logs/")
    void neverWritesTheApiKeyToDisk() throws Exception {
        // Everything that touches the key: a parse (which logs a row), a suggestion, the status
        // endpoint, the log listing, and an attempt to smuggle it in as an ordinary setting.
        mockMvc.perform(authed(post("/api/v1/ai/parse")).content(json(Map.of("text", "cà phê 45k"))))
                .andExpect(status().isOk());
        mockMvc.perform(authed(post("/api/v1/ai/suggest-category"))
                        .content(json(Map.of("note", "trà sữa", "type", "EXPENSE"))))
                .andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/ai/status"))).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/ai/logs"))).andExpect(status().isOk());
        mockMvc.perform(authed(put("/api/v1/settings")).content(json(Map.of("ai.api_key", API_KEY))))
                .andExpect(status().isBadRequest());

        List<Path> leaking = Stream.concat(filesUnder(DATA_DIR), filesUnder(LOG_DIR))
                .filter(ApiKeyLeakIT::contains)
                .toList();

        assertThat(leaking)
                .as("NFR-SEC-01: không file nào trong data/ hoặc logs/ được chứa API key")
                .isEmpty();
    }

    @Test
    @DisplayName("Bản ghi ai_parse_log và bảng setting đều không mang API key")
    void neitherLogRowsNorSettingsCarryTheKey() throws Exception {
        mockMvc.perform(authed(post("/api/v1/ai/parse")).content(json(Map.of("text", "cà phê 45k"))))
                .andExpect(status().isOk());

        String logs = body(mockMvc.perform(authed(get("/api/v1/ai/logs"))).andReturn());
        String settings = body(mockMvc.perform(authed(get("/api/v1/settings"))).andReturn());

        assertThat(logs).doesNotContain(API_KEY);
        assertThat(settings).doesNotContain(API_KEY);
    }

    @Test
    @DisplayName("Lỗi trả về từ dịch vụ AI không lộ API key trong thông báo")
    void errorMessagesDoNotEchoTheKey() throws Exception {
        String body = body(mockMvc.perform(authed(post("/api/v1/ai/test-connection"))).andReturn());

        assertThat(body).doesNotContain(API_KEY).doesNotContain("sk-ant");
    }

    /** Every regular file under {@code directory}, or nothing when it was never created. */
    private static Stream<Path> filesUnder(Path directory) {
        if (!Files.isDirectory(directory)) {
            return Stream.empty();
        }
        try (var walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException("Không quét được " + directory, e);
        }
    }

    /**
     * Whether the raw bytes of {@code file} contain the key.
     *
     * <p>Read as bytes and decoded leniently rather than as text: a SQLite file is not UTF-8, and a
     * strict decoder would throw before it ever got to the string that matters.
     */
    private static boolean contains(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).contains(API_KEY);
        } catch (IOException e) {
            // A rotating log file can vanish mid-walk; that is not a leak.
            return false;
        }
    }

    /**
     * Replaces the real provider client so no request leaves the machine.
     *
     * <p>Reports itself as configured, because that is the state under test - an unconfigured
     * backend has no key to leak.
     */
    @TestConfiguration
    static class StubbedProvider {

        @Bean
        @Primary
        AiClient stubAiClient() {
            return new AiClient() {
                @Override
                public boolean isConfigured() {
                    return true;
                }

                @Override
                public String defaultModel() {
                    return "claude-opus-5";
                }

                @Override
                public AiCompletion complete(AiRequest request) {
                    throw new com.lifehub.domain.ai.AiUnavailableException(
                            com.lifehub.domain.ai.AiErrorCode.TIMEOUT,
                            "Không kết nối được tới dịch vụ AI");
                }
            };
        }
    }
}
