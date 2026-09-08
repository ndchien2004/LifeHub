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
    private final DashboardService dashboardService = mock(DashboardService.class);
    private final BootstrapService service =
            new BootstrapService(settingService, reminderService, dashboardService);

    @Test
    @DisplayName("Bootstrap trả settings, nhắc hẹn bị lỡ và số liệu dashboard trong một lần gọi")
    void returnsEverythingTheRendererNeeds() {
        when(settingService.findAll()).thenReturn(Map.of("app.theme", "SYSTEM"));
        when(reminderService.findMissed()).thenReturn(List.of());
        when(dashboardService.load())
                .thenReturn(new DashboardData(3, 1, 2, 450_000L, 15_000_000L, List.of()));

        BootstrapData data = service.load();

        assertThat(data.settings()).containsEntry("app.theme", "SYSTEM");
        assertThat(data.aiConfigured()).as("AI chưa cấu hình được cho tới Phase 4").isFalse();
        assertThat(data.missedReminders()).as("Không có nhắc hẹn bị lỡ").isEmpty();
        assertThat(data.dashboard().todayTasks()).isEqualTo(3);
        assertThat(data.dashboard().monthExpense()).isEqualTo(450_000L);
    }

    @Test
    @DisplayName("Dashboard lỗi thì bootstrap vẫn trả về được, chỉ thiếu phần số liệu")
    void degradesToNullDashboardWhenAggregationFails() {
        when(settingService.findAll()).thenReturn(Map.of());
        when(reminderService.findMissed()).thenReturn(List.of());
        when(dashboardService.load()).thenThrow(new IllegalStateException("aggregation exploded"));

        BootstrapData data = service.load();

        assertThat(data.dashboard())
                .as("Ứng dụng phải mở được ngay cả khi không tổng hợp được số liệu")
                .isNull();
    }
}
