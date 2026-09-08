-- V3 — Phase 2: module Calendar & Reminder (FR-CAL-01→11, FR-SYS-07).
--
-- Ghi chu ve moc thoi gian: giong V2, cac cot TIMESTAMP KHONG dat `DEFAULT CURRENT_TIMESTAMP`.
-- SQLite ghi CURRENT_TIMESTAMP thanh chuoi 'YYYY-MM-DD HH:MM:SS' con Hibernate ghi Instant
-- theo dinh dang cua no; tron hai dinh dang se lam hong moi phep so sanh khoang thoi gian.

-- Event lap lai KHONG duoc materialize (03-DATA-MODEL.md §2.3): chi luu mot ban ghi master
-- kem `rrule`, cac instance sinh dong luc truy van qua RecurrenceExpander.
CREATE TABLE event (
    id          TEXT PRIMARY KEY,
    task_id     TEXT REFERENCES task (id) ON DELETE SET NULL,
    title       TEXT NOT NULL,
    description TEXT,
    location    TEXT,
    start_at    TIMESTAMP NOT NULL,
    end_at      TIMESTAMP NOT NULL,
    all_day     BOOLEAN   NOT NULL DEFAULT 0,
    rrule       TEXT,
    timezone    TEXT      NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    deleted_at  TIMESTAMP,
    -- Su kien ca ngay: start_at = 00:00 gio dia phuong, end_at = 00:00 ngay ke tiep,
    -- vua thoa rang buoc nay (03-DATA-MODEL.md §6, muc M-13).
    CONSTRAINT ck_event_range CHECK (end_at > start_at)
);

CREATE INDEX idx_event_range ON event (start_at, end_at, deleted_at);
CREATE INDEX idx_event_task  ON event (task_id);

-- Ghi de hoac huy mot instance cu the cua chuoi lap (SD-05).
-- Chi ghi de duoc new_start_at / new_end_at / new_title — vi vay scope THIS_ONLY tren UI
-- chi cho sua thoi gian va tieu de (03-DATA-MODEL.md §6, muc M-19).
CREATE TABLE event_exception (
    id                TEXT PRIMARY KEY,
    event_id          TEXT      NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    original_start_at TIMESTAMP NOT NULL,
    cancelled         BOOLEAN   NOT NULL DEFAULT 0,
    new_start_at      TIMESTAMP,
    new_end_at        TIMESTAMP,
    new_title         TEXT
);

CREATE UNIQUE INDEX idx_event_exception_occurrence
    ON event_exception (event_id, original_start_at);

CREATE TABLE reminder (
    id             TEXT PRIMARY KEY,
    event_id       TEXT REFERENCES event (id) ON DELETE CASCADE,
    task_id        TEXT REFERENCES task (id) ON DELETE CASCADE,
    trigger_at     TIMESTAMP NOT NULL,
    offset_minutes INTEGER   NOT NULL DEFAULT 15,
    status         TEXT      NOT NULL DEFAULT 'PENDING',
    fired_at       TIMESTAMP,
    created_at     TIMESTAMP NOT NULL,
    CONSTRAINT ck_reminder_ref    CHECK (event_id IS NOT NULL OR task_id IS NOT NULL),
    CONSTRAINT ck_reminder_status CHECK (status IN ('PENDING', 'FIRED', 'SNOOZED', 'DISMISSED', 'EXPIRED'))
);

-- Index quan trong nhat cua phase: ReminderScheduler quet moi 30 giay va khong duoc
-- quet toan bang (SD-03).
CREATE INDEX idx_reminder_pending ON reminder (status, trigger_at);
CREATE INDEX idx_reminder_event   ON reminder (event_id);
CREATE INDEX idx_reminder_task    ON reminder (task_id);
