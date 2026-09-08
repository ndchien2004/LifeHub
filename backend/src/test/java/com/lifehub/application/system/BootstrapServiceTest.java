package com.lifehub.application.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Shape of the startup payload. */
class BootstrapServiceTest {

    private final SettingService settingService = mock(SettingService.class);
    private final BootstrapService service = new BootstrapService(settingService);

    @Test
    @DisplayName("Bootstrap trả settings hiện có kèm các phần chưa tới phase dưới dạng rỗng")
    void returnsSettingsAndStablePlaceholders() {
        when(settingService.findAll()).thenReturn(Map.of("app.theme", "SYSTEM"));

        BootstrapData data = service.load();

        assertThat(data.settings()).containsEntry("app.theme", "SYSTEM");
        assertThat(data.aiConfigured()).as("AI chưa cấu hình được cho tới Phase 4").isFalse();
        assertThat(data.missedReminders()).as("Reminder bắt đầu từ Phase 2").isEmpty();
        assertThat(data.dashboard()).as("Dashboard bắt đầu từ Phase 3").isNull();
    }
}
