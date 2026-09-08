-- V1 — Phase 0: bang setting va gia tri mac dinh.
--
-- Ghi chu ve PRAGMA (03-DATA-MODEL.md §5):
-- Flyway chay moi migration trong mot transaction, ma `PRAGMA journal_mode = WAL`
-- khong the chay ben trong transaction. Vi vay toan bo pragma bat buoc duoc dat
-- bang tham so tren `spring.datasource.url` — cho hieu luc tren MOI ket noi, ke ca
-- ket noi cua chinh Flyway. `SqliteConfig` doc lai va bao loi neu foreign_keys tat.

CREATE TABLE setting (
    key        TEXT PRIMARY KEY,
    value      TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Gia tri mac dinh theo 03-DATA-MODEL.md §2.11.
-- `ai.model` chua co gia tri: user chon trong Settings o Phase 4.
-- `db.schema_version` do Flyway tu quan ly trong bang flyway_schema_history.
INSERT INTO setting (key, value) VALUES
    ('app.theme',              'SYSTEM'),
    ('app.timezone',           'Asia/Ho_Chi_Minh'),
    ('app.week_start',         'MONDAY'),
    ('app.currency',           'VND'),
    ('ai.enabled',             'true'),
    ('ai.weekly_insight_cron', '0 0 20 * * SUN'),
    ('backup.dir',             'data/backups'),
    ('backup.keep_count',      '30');
