package com.lifehub.api.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifehub.application.system.SettingService;
import com.lifehub.support.ApiIntegrationTest;
import com.lifehub.support.TestDatabase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** FR-SYS-09 — the settings endpoints and the whitelist that guards them. */
class SettingsApiIT extends ApiIntegrationTest {

    private static final String DATABASE_URL = TestDatabase.freshUrl();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    private SettingService settingService;

    /**
     * Restores the seeded values.
     *
     * <p>These tests share one database file, and several of them change the very settings the
     * others assert on. Resetting here rather than relying on execution order is what keeps a
     * failure meaningful: it points at the endpoint, not at whichever test ran first.
     */
    @BeforeEach
    void restoreSeededValues() {
        settingService.putAll(Map.of(
                "app.theme", "SYSTEM",
                "app.timezone", "Asia/Ho_Chi_Minh",
                "app.week_start", "MONDAY",
                "app.currency", "VND",
                "ai.enabled", "true"));
    }

    @Test
    @DisplayName("GET /settings trả về toàn bộ giá trị đã seed")
    void returnsEverySeededSetting() throws Exception {
        mockMvc.perform(authed(get("/api/v1/settings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings['app.theme']").value("SYSTEM"))
                .andExpect(jsonPath("$.data.settings['app.currency']").value("VND"))
                .andExpect(jsonPath("$.data.settings['ai.enabled']").value("true"))
                .andExpect(jsonPath("$.data.requiresRestart").value(false));
    }

    @Test
    @DisplayName("PUT /settings cập nhật nhiều khóa cùng lúc và trả về trạng thái mới")
    void updatesSeveralSettingsAtOnce() throws Exception {
        mockMvc.perform(authed(put("/api/v1/settings"))
                        .content(json(Map.of(
                                "app.theme", "DARK",
                                "app.currency", "USD",
                                "ai.model", "claude-haiku-4-5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings['app.theme']").value("DARK"))
                .andExpect(jsonPath("$.data.settings['app.currency']").value("USD"))
                .andExpect(jsonPath("$.data.settings['ai.model']").value("claude-haiku-4-5"))
                .andExpect(jsonPath("$.data.requiresRestart").value(false));
    }

    @Test
    @DisplayName("Đổi múi giờ báo cần khởi động lại vì TimeConfig chỉ đọc nó lúc khởi động")
    void reportsWhenARestartIsNeeded() throws Exception {
        mockMvc.perform(authed(put("/api/v1/settings"))
                        .content(json(Map.of("app.timezone", "Asia/Tokyo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresRestart").value(true));
    }

    @Test
    @DisplayName("Giá trị có khoảng trắng thừa được cắt trước khi lưu, không lưu nguyên")
    void trimsValuesBeforeStoringThem() throws Exception {
        // Validation already trims before checking, so " DARK " passes. If the write kept the raw
        // string, every later comparison against "DARK" would fail - including the one the renderer
        // uses to restore the theme at startup.
        mockMvc.perform(authed(put("/api/v1/settings"))
                        .content(json(Map.of("app.theme", "  DARK  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings['app.theme']").value("DARK"));

        mockMvc.perform(authed(get("/api/v1/settings")))
                .andExpect(jsonPath("$.data.settings['app.theme']").value("DARK"));
    }

    @Test
    @DisplayName("Khóa lạ bị từ chối thay vì âm thầm tạo một setting không ai đọc")
    void rejectsUnknownKeys() throws Exception {
        mockMvc.perform(authed(put("/api/v1/settings"))
                        .content(json(Map.of("app.theme_color", "#ff0000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Giá trị sai kiểu bị từ chối, kèm tên trường sai")
    void rejectsInvalidValues() throws Exception {
        mockMvc.perform(authed(put("/api/v1/settings"))
                        .content(json(Map.of("app.theme", "NEON"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("app.theme"));

        mockMvc.perform(authed(put("/api/v1/settings"))
                        .content(json(Map.of("app.timezone", "Mars/Olympus"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("app.timezone"));

        mockMvc.perform(authed(put("/api/v1/settings"))
                        .content(json(Map.of("backup.keep_count", "0"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("backup.keep_count"));
    }

    @Test
    @DisplayName("Một giá trị sai thì không khóa nào được ghi")
    void appliesNothingWhenAnyValueIsInvalid() throws Exception {
        mockMvc.perform(authed(put("/api/v1/settings"))
                        .content(json(Map.of("app.currency", "JPY", "app.week_start", "FUNDAY"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authed(get("/api/v1/settings")))
                .andExpect(jsonPath("$.data.settings['app.currency']")
                        .value("VND"));
    }

    @Test
    @DisplayName("API key không bao giờ nằm trong bảng setting")
    void neverStoresTheApiKey() throws Exception {
        mockMvc.perform(authed(put("/api/v1/settings"))
                        .content(json(Map.of("ai.api_key", "sk-ant-secret"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(authed(get("/api/v1/settings")))
                .andExpect(jsonPath("$.data.settings['ai.api_key']").doesNotExist());
    }

    @Test
    @DisplayName("PUT /settings cũng yêu cầu token như mọi endpoint khác")
    void requiresTheAppToken() throws Exception {
        mockMvc.perform(put("/api/v1/settings")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(Map.of("app.theme", "DARK"))))
                .andExpect(status().isUnauthorized());
    }
}
