package com.lifehub.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Directory resolution precedence: command line beats environment beats default. */
class AppPathsTest {

    private static final String[] NO_ARGS = {};

    @Test
    @DisplayName("Tham số dòng lệnh có ưu tiên cao nhất")
    void commandLineArgumentWins() {
        String[] args = {"--server.port=51234", "--app.data-dir=D:/custom/data"};

        assertThat(AppPaths.resolve(args, Map.of("LIFEHUB_DATA_DIR", "from-env"), "app.data-dir", "LIFEHUB_DATA_DIR", "data"))
                .isEqualTo("D:/custom/data");
    }

    @Test
    @DisplayName("Không có tham số thì dùng biến môi trường")
    void environmentVariableIsTheSecondChoice() {
        assertThat(AppPaths.resolve(NO_ARGS, Map.of("LIFEHUB_LOG_DIR", "C:/logs"), "app.log-dir", "LIFEHUB_LOG_DIR", "logs"))
                .isEqualTo("C:/logs");
    }

    @Test
    @DisplayName("Không có gì thì dùng mặc định")
    void fallsBackToTheDefault() {
        assertThat(AppPaths.resolve(NO_ARGS, Map.of(), "app.data-dir", "LIFEHUB_DATA_DIR", "data"))
                .isEqualTo("data");
    }

    @Test
    @DisplayName("Tham số rỗng bị bỏ qua, không tạo thư mục tên rỗng")
    void ignoresABlankArgument() {
        String[] args = {"--app.data-dir="};

        assertThat(AppPaths.resolve(args, Map.of(), "app.data-dir", "LIFEHUB_DATA_DIR", "data"))
                .isEqualTo("data");
    }

    @Test
    @DisplayName("Tạo sẵn thư mục data và logs — driver SQLite không tự tạo thư mục cha")
    void createsBothDirectories(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("nested/data");
        Path logDir = tempDir.resolve("nested/logs");

        AppPaths.prepareDirectories(
                new String[] {"--app.data-dir=" + dataDir, "--app.log-dir=" + logDir}, Map.of());

        assertThat(Files.isDirectory(dataDir)).isTrue();
        assertThat(Files.isDirectory(logDir)).isTrue();
    }
}
