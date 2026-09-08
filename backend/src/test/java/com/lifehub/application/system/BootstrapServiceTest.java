package com.lifehub.application.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lifehub.application.calendar.ReminderService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Shape of the startup payload. */
class BootstrapServiceTest {

    private final SettingService settingService = mock(SettingService.class);
    private final ReminderService reminderService = mock(ReminderService.class);
    private final BootstrapService service = new BootstrapService(settingService, reminderService);

    @Test
    @DisplayName("Bootstrap trả settings hiện có kèm các phần chưa tới phase dưới dạng rỗng")
    void returnsSettingsAndStablePlaceholders() {
        when(settingService.findAll()).thenReturn(Map.of("app.theme", "SYSTEM"));
        when(reminderService.findMissed()).thenReturn(List.of());

        BootstrapData data = service.load();

        assertThat(data.settings()).containsEntry("app.theme", "SYSTEM");
        assertThat(data.aiConfigured()).as("AI chưa cấu hình được cho tới Phase 4").isFalse();
        assertThat(data.missedReminders()).as("Không có nhắc hẹn bị lỡ").isEmpty();
        assertThat(data.dashboard()).as("Dashboard bắt đầu từ Phase 3").isNull();
    }
}
