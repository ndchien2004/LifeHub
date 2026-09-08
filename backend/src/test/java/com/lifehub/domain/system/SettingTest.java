package com.lifehub.domain.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A setting records when it last changed, so Settings screens can show it. */
class SettingTest {

    @Test
    @DisplayName("Setting mới có mốc cập nhật ngay từ lúc tạo")
    void stampsUpdatedAtOnCreation() {
        Setting setting = new Setting("app.theme", "DARK");

        assertThat(setting.getKey()).isEqualTo("app.theme");
        assertThat(setting.getValue()).isEqualTo("DARK");
        assertThat(setting.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Đổi giá trị thì đẩy mốc cập nhật lên")
    void movesUpdatedAtForwardWhenValueChanges() throws InterruptedException {
        Setting setting = new Setting("app.theme", "SYSTEM");
        Instant before = setting.getUpdatedAt();

        Thread.sleep(5);
        setting.setValue("LIGHT");

        assertThat(setting.getValue()).isEqualTo("LIGHT");
        assertThat(setting.getUpdatedAt()).isAfter(before);
    }

    @Test
    @DisplayName("Giá trị null hợp lệ — dùng cho setting chưa cấu hình như ai.model")
    void allowsAnUnsetValue() {
        Setting setting = new Setting("ai.model", null);

        assertThat(setting.getValue()).isNull();
    }
}
