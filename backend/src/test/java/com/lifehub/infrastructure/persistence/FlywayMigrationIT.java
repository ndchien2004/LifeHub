package com.lifehub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifehub.support.TestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** T0-05 — Flyway created the Phase 0 schema, and the mandatory pragmas are actually in effect. */
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIT {

    private static final String DATABASE_URL = TestDatabase.freshUrl();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    private DataSource dataSource;

    /**
     * The migration count grows by one each phase. Asserting the exact set, rather than merely
     * that some migrations ran, is what catches a migration accidentally renamed, dropped or
     * duplicated - the failure mode that becomes unrecoverable once a user has run the old version.
     */
    @Test
    @DisplayName("T0-05 — mọi migration đã chạy thành công, đúng số lượng của phase hiện tại")
    void migrationCreatedTheCoreSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "setting")).isTrue();
            assertThat(tableExists(connection, "flyway_schema_history")).isTrue();

            assertThat(scalar(connection, "SELECT COUNT(*) FROM flyway_schema_history"))
                    .as("Phase 0 có V1, Phase 1 thêm V2")
                    .isEqualTo("2");
            assertThat(scalar(connection,
                            "SELECT GROUP_CONCAT(version) FROM "
                                    + "(SELECT version FROM flyway_schema_history ORDER BY installed_rank)"))
                    .isEqualTo("1,2");
            assertThat(scalar(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0"))
                    .as("không migration nào được phép thất bại")
                    .isEqualTo("0");
        }
    }

    @Test
    @DisplayName("V2 tạo đủ bảng module Task kèm index bắt buộc")
    void migrationCreatedTheTaskModule() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "project")).isTrue();
            assertThat(tableExists(connection, "task")).isTrue();
            assertThat(tableExists(connection, "tag")).isTrue();
            assertThat(tableExists(connection, "task_tag")).isTrue();

            assertThat(scalar(connection,
                            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name IN "
                                    + "('idx_task_due','idx_task_status','idx_task_project','idx_tag_name')"))
                    .isEqualTo("4");
        }
    }

    @Test
    @DisplayName("V1 seed đủ 8 setting mặc định theo 03-DATA-MODEL.md §2.11")
    void seededDefaultSettings() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(scalar(connection, "SELECT COUNT(*) FROM setting")).isEqualTo("8");
            assertThat(scalar(connection, "SELECT value FROM setting WHERE key = 'app.week_start'"))
                    .isEqualTo("MONDAY");
            assertThat(scalar(connection, "SELECT value FROM setting WHERE key = 'backup.keep_count'"))
                    .isEqualTo("30");
        }
    }

    @Test
    @DisplayName("Pragma bắt buộc có hiệu lực: WAL bật, foreign_keys bật (03-DATA-MODEL.md §5)")
    void mandatoryPragmasAreActive() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(scalar(connection, "PRAGMA journal_mode")).isEqualToIgnoringCase("wal");
            assertThat(scalar(connection, "PRAGMA foreign_keys")).isEqualTo("1");
            assertThat(scalar(connection, "PRAGMA busy_timeout")).isEqualTo("5000");
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        return scalar(connection,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '" + table + "'")
                .equals("1");
    }

    private String scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
