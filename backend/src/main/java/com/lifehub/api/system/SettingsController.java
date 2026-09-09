package com.lifehub.api.system;

import com.lifehub.api.common.ApiResponse;
import com.lifehub.application.system.SettingService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Settings endpoints (06-API-SPEC.md 2, FR-SYS-09). */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingService settingService;

    public SettingsController(SettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping
    public ApiResponse<SettingsResponse> get() {
        return ApiResponse.ok(new SettingsResponse(settingService.findAll(), false));
    }

    /**
     * Updates several settings at once.
     *
     * <p>The body is the same key to value shape {@code GET} returns, so the screen can send back
     * only what changed. Unknown keys are rejected rather than stored - see {@link SettingService}.
     */
    @PutMapping
    public ApiResponse<SettingsResponse> update(@RequestBody Map<String, String> updates) {
        Map<String, String> settings = settingService.putAll(updates);
        return ApiResponse.ok(
                new SettingsResponse(settings, SettingService.requiresRestart(updates.keySet())));
    }

    /**
     * @param settings every stored setting, so the caller never has to merge
     * @param requiresRestart true when something in this update is only read at startup, and the
     *     user needs to be offered a restart for it to take effect
     */
    public record SettingsResponse(Map<String, String> settings, boolean requiresRestart) {
    }
}
