-- V2 — Phase 1: module Task & Project (FR-TSK-01→12, FR-PRJ-01→04).
--
-- Ghi chu ve moc thoi gian: cac cot TIMESTAMP KHONG dat `DEFAULT CURRENT_TIMESTAMP`.
-- SQLite ghi CURRENT_TIMESTAMP thanh chuoi 'YYYY-MM-DD HH:MM:SS', con Hibernate ghi
-- Instant theo dinh dang cua no. Tron hai dinh dang trong cung mot cot se lam hong
-- moi phep so sanh khoang ngay (loc theo due_at, sap xep). Hibernate luon set gia tri
-- qua @PrePersist/@PreUpdate nen mac dinh o tang SQL la thua va nguy hiem.

CREATE TABLE project (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    color       TEXT NOT NULL DEFAULT '#6366f1',
    description TEXT,
    status      TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    deleted_at  TIMESTAMP,
    CONSTRAINT ck_project_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_project_status ON project (status, deleted_at);

CREATE TABLE task (
    id               TEXT PRIMARY KEY,
    project_id       TEXT REFERENCES project (id) ON DELETE SET NULL,
    parent_id        TEXT REFERENCES task (id) ON DELETE CASCADE,
    title            TEXT NOT NULL,
    description      TEXT,
    priority         TEXT NOT NULL DEFAULT 'MEDIUM',
    status           TEXT NOT NULL DEFAULT 'TODO',
    due_at           TIMESTAMP,
    estimate_minutes INTEGER,
    -- Tich luy tu pomodoro. Tao theo dung schema chot nhung chua co FR nao dung
    -- va khong expose ra API o v1.0 (03-DATA-MODEL.md §6, muc M-14).
    actual_minutes   INTEGER,
    -- Task lap lai theo RRULE — cot tao san, logic thuoc Phase 2 (FR-TSK-13).
    rrule            TEXT,
    sort_order       INTEGER NOT NULL DEFAULT 0,
    completed_at     TIMESTAMP,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,
    deleted_at       TIMESTAMP,
    CONSTRAINT ck_task_not_own_parent CHECK (parent_id IS NULL OR parent_id != id),
    CONSTRAINT ck_task_priority       CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_task_status         CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_task_estimate       CHECK (estimate_minutes IS NULL OR estimate_minutes > 0)
);

CREATE INDEX idx_task_due     ON task (due_at, deleted_at);
CREATE INDEX idx_task_status  ON task (status, deleted_at);
CREATE INDEX idx_task_project ON task (project_id);
CREATE INDEX idx_task_parent  ON task (parent_id);

CREATE TABLE tag (
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    color      TEXT NOT NULL DEFAULT '#94a3b8',
    created_at TIMESTAMP NOT NULL
);

-- FR-PRJ-04: ten tag la duy nhat. `tag` khong co deleted_at — day la ngoai le hard
-- delete co chu y (03-DATA-MODEL.md §6, muc C-5b), nen unique index thuong la du.
CREATE UNIQUE INDEX idx_tag_name ON tag (name);

-- Bang noi task <-> tag (03-DATA-MODEL.md §6, muc C-3).
CREATE TABLE task_tag (
    task_id TEXT NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    tag_id  TEXT NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, tag_id)
);

CREATE INDEX idx_task_tag_tag ON task_tag (tag_id);
