package com.lifehub.api.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifehub.support.TestDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** T0-02, T0-03, T0-04 — the bootstrap endpoint and the token guard in front of it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BootstrapApiIT {

    private static final String DATABASE_URL = TestDatabase.freshUrl();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("T0-02 — token đúng trả 200 kèm settings đã seed")
    void returnsSettingsWhenTokenIsValid() throws Exception {
        mockMvc.perform(get("/api/v1/bootstrap").header("X-App-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.settings['app.theme']").value("SYSTEM"))
                .andExpect(jsonPath("$.data.settings['app.currency']").value("VND"))
                .andExpect(jsonPath("$.data.settings['app.timezone']").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.data.aiConfigured").value(false))
                .andExpect(jsonPath("$.data.missedReminders").isEmpty())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("T0-03 — thiếu header token trả 401 UNAUTHORIZED")
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/bootstrap"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("T0-04 — token sai trả 401")
    void rejectsRequestWithWrongToken() throws Exception {
        mockMvc.perform(get("/api/v1/bootstrap").header("X-App-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Health check nằm ngoài vùng bảo vệ token, Electron poll được khi khởi động")
    void healthEndpointIsReachableWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("CORS preflight được trả lời mà không cần token — nếu không, renderer không gọi được API")
    void answersCorsPreflightWithoutToken() throws Exception {
        mockMvc.perform(options("/api/v1/bootstrap")
                        .header("Origin", "http://127.0.0.1:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-App-Token, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("X-App-Token")));
    }

    @Test
    @DisplayName("Trang tải từ file:// trong bản đóng gói (Origin: null) cũng được phép")
    void allowsTheFileOriginUsedByThePackagedApp() throws Exception {
        mockMvc.perform(options("/api/v1/bootstrap")
                        .header("Origin", "null")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-App-Token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "null"));
    }

    @Test
    @DisplayName("Preflight được miễn token, nhưng request thật thì KHÔNG")
    void stillRequiresTheTokenOnTheRealRequest() throws Exception {
        mockMvc.perform(get("/api/v1/bootstrap").header("Origin", "http://127.0.0.1:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Lỗi không bao giờ lộ stack trace ra client (NFR-USE-03)")
    void errorBodyCarriesNoStackTrace() throws Exception {
        String body = mockMvc.perform(get("/api/v1/bootstrap"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("Exception")
                .doesNotContain("at com.lifehub")
                .doesNotContain("stackTrace")
                .doesNotContain("java.lang");
    }
}
