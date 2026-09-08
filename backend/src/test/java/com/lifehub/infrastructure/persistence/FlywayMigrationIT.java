package com.lifehub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                    .as("Phase 0 có V1, Phase 1 thêm V2, Phase 2 thêm V3, Phase 3 thêm V4 và V5")
                    .isEqualTo("5");
            assertThat(scalar(connection,
                            "SELECT GROUP_CONCAT(version) FROM "
                                    + "(SELECT version FROM flyway_schema_history ORDER BY installed_rank)"))
                    .isEqualTo("1,2,3,4,5");
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
    @DisplayName("V3 tạo đủ bảng module Calendar kèm index bắt buộc")
    void migrationCreatedTheCalendarModule() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "event")).isTrue();
            assertThat(tableExists(connection, "event_exception")).isTrue();
            assertThat(tableExists(connection, "reminder")).isTrue();

            assertThat(scalar(connection,
                            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name IN "
                                    + "('idx_event_range','idx_reminder_pending','idx_event_exception_occurrence')"))
                    .as("idx_reminder_pending là index scheduler phụ thuộc hoàn toàn (SD-03)")
                    .isEqualTo("3");
        }
    }

    @Test
    @DisplayName("V4 tạo đủ bảng module Finance kèm index và ràng buộc bắt buộc")
    void migrationCreatedTheFinanceModule() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "wallet")).isTrue();
            assertThat(tableExists(connection, "category")).isTrue();
            assertThat(tableExists(connection, "transaction")).isTrue();
            assertThat(tableExists(connection, "transaction_tag")).isTrue();
            assertThat(tableExists(connection, "budget")).isTrue();
            assertThat(tableExists(connection, "recurring_rule")).isTrue();

            assertThat(scalar(connection,
                            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name IN "
                                    + "('idx_txn_occurred','idx_txn_category','idx_txn_wallet',"
                                    + "'idx_wallet_name','idx_category_unique','idx_budget_active')"))
                    .as("idx_txn_occurred là index danh sách giao dịch phụ thuộc (NFR-PERF-03)")
                    .isEqualTo("6");
        }
    }

    /**
     * The CHECK constraints are the last line of defence for money. Asserting them here rather than
     * only in a service test proves the file on disk can never hold a broken row, even if a future
     * change bypasses the domain layer.
     */
    @Test
    @DisplayName("V4 chặn ở tầng database: số tiền ≤ 0 và chuyển khoản cùng một ví")
    void financeConstraintsAreEnforcedByTheDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO wallet (id, name, type, initial_balance, currency, is_default, sort_order, created_at, updated_at) "
                            + "VALUES ('w-check', 'Ví kiểm tra', 'CASH', 0, 'VND', 0, 0, 0, 0)");

            assertThatThrownBy(() -> statement.executeUpdate(
                            "INSERT INTO \"transaction\" (id, wallet_id, category_id, type, amount, occurred_at, source, created_at, updated_at) "
                                    + "VALUES ('t-neg', 'w-check', '01900000-0000-7000-8000-00000000e001', 'EXPENSE', -1, 0, 'MANUAL', 0, 0)"))
                    .hasMessageContaining("CHECK constraint failed");

            assertThatThrownBy(() -> statement.executeUpdate(
                            "INSERT INTO \"transaction\" (id, wallet_id, to_wallet_id, type, amount, occurred_at, source, created_at, updated_at) "
                                    + "VALUES ('t-self', 'w-check', 'w-check', 'TRANSFER', 1000, 0, 'MANUAL', 0, 0)"))
                    .hasMessageContaining("CHECK constraint failed");
        }
    }

    @Test
    @DisplayName("V5 seed đủ bộ danh mục hệ thống hai cấp (FR-FIN-03)")
    void seededSystemCategories() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(scalar(connection, "SELECT COUNT(*) FROM category WHERE is_system = 1")).isEqualTo("26");
            assertThat(scalar(connection,
                            "SELECT COUNT(*) FROM category WHERE type = 'INCOME' AND parent_id IS NULL"))
                    .isEqualTo("5");
            assertThat(scalar(connection,
                            "SELECT COUNT(*) FROM category c WHERE c.parent_id IS NOT NULL "
                                    + "AND EXISTS (SELECT 1 FROM category p WHERE p.id = c.parent_id AND p.parent_id IS NOT NULL)"))
                    .as("danh mục chỉ được sâu 2 cấp")
                    .isEqualTo("0");
            assertThat(scalar(connection,
                            "SELECT name FROM category WHERE id = '01900000-0000-7000-8000-00000000e103'"))
                    .isEqualTo("Cà phê");
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
