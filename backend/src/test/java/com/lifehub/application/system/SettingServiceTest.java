package com.lifehub.application.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifehub.domain.system.Setting;
import com.lifehub.domain.system.SettingRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Settings read and write semantics, independent of storage. */
class SettingServiceTest {

    private final SettingRepository repository = mock(SettingRepository.class);
    private final SettingService service = new SettingService(repository);

    @Test
    @DisplayName("findAll trả map key→value, giữ nguyên thứ tự repository trả về")
    void exposesSettingsAsAnOrderedMap() {
        when(repository.findAll()).thenReturn(List.of(
                new Setting("ai.enabled", "true"),
                new Setting("app.currency", "VND"),
                new Setting("app.theme", "SYSTEM")));

        assertThat(service.findAll())
                .containsExactly(
                        org.assertj.core.data.MapEntry.entry("ai.enabled", "true"),
                        org.assertj.core.data.MapEntry.entry("app.currency", "VND"),
                        org.assertj.core.data.MapEntry.entry("app.theme", "SYSTEM"));
    }

    @Test
    @DisplayName("get trả giá trị đã lưu khi key tồn tại")
    void readsAnExistingValue() {
        when(repository.findByKey("app.theme")).thenReturn(Optional.of(new Setting("app.theme", "DARK")));

        assertThat(service.get("app.theme", "SYSTEM")).isEqualTo("DARK");
    }

    @Test
    @DisplayName("get trả giá trị mặc định khi key chưa có, không ném lỗi")
    void fallsBackToTheDefaultForAnUnknownKey() {
        when(repository.findByKey("ai.model")).thenReturn(Optional.empty());

        assertThat(service.get("ai.model", "chua-cau-hinh")).isEqualTo("chua-cau-hinh");
    }

    @Test
    @DisplayName("put tạo mới khi key chưa tồn tại")
    void createsASettingThatDoesNotExistYet() {
        when(repository.findByKey("app.theme")).thenReturn(Optional.empty());

        service.put("app.theme", "LIGHT");

        ArgumentCaptor<Setting> saved = ArgumentCaptor.forClass(Setting.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getKey()).isEqualTo("app.theme");
        assertThat(saved.getValue().getValue()).isEqualTo("LIGHT");
    }

    @Test
    @DisplayName("put ghi đè bản ghi cũ thay vì tạo bản trùng key")
    void overwritesAnExistingSetting() {
        Setting existing = new Setting("app.theme", "SYSTEM");
        when(repository.findByKey("app.theme")).thenReturn(Optional.of(existing));
        when(repository.save(any(Setting.class))).thenReturn(existing);

        service.put("app.theme", "DARK");

        verify(repository).save(existing);
        assertThat(existing.getValue()).isEqualTo("DARK");
    }
}
