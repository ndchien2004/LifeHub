-- V4 — Phase 3: module Finance (FR-FIN-01→13, FR-SYS-01).
--
-- Ghi chu ve moc thoi gian: giong V2/V3, cac cot TIMESTAMP KHONG dat `DEFAULT CURRENT_TIMESTAMP`.
-- Hibernate luon set gia tri qua @PrePersist/@PreUpdate; tron dinh dang cua SQLite voi dinh dang
-- cua Hibernate se lam hong moi phep so sanh khoang thoi gian.
--
-- Ghi chu ve tien te: moi cot so tien la INTEGER, don vi dong (FR-FIN-06). Khong bao gio dung
-- REAL cho tien — 0.1 + 0.2 != 0.3 trong so thuc nhi phan, va mot so du sai mot dong la sai.
--
-- Ghi chu ve ngay: SQLite khong co kieu DATE that. Cac cot `start_date`, `next_run_date`,
-- `last_run_date` luu TEXT 'yyyy-MM-dd' (03-DATA-MODEL.md §6, muc M-21).

-- Quy luat sinh giao dich dinh ky (FR-FIN-13). Tao TRUOC `transaction` vi bang do co khoa
-- ngoai tro toi day va `PRAGMA foreign_keys = ON` doi bang dich phai ton tai (PROGRESS.md, B-1).
-- Danh sach cot lay dung ERD §1, khong them cot (03-DATA-MODEL.md §6, muc C-3).
CREATE TABLE recurring_rule (
    id             TEXT PRIMARY KEY,
    rrule          TEXT    NOT NULL,
    template_json  TEXT    NOT NULL,
    next_run_date  TEXT,
    last_run_date  TEXT,
    is_active      BOOLEAN NOT NULL DEFAULT 1
);

-- Job quet moi gio chi quan tam cac quy luat con hieu luc va den han.
CREATE INDEX idx_recurring_rule_due ON recurring_rule (is_active, next_run_date);

CREATE TABLE wallet (
    id              TEXT    PRIMARY KEY,
    name            TEXT    NOT NULL,
    type            TEXT    NOT NULL DEFAULT 'CASH',
    -- So du hien tai KHONG luu o day. Tinh dong theo cong thuc 03-DATA-MODEL.md §2.6 de
    -- khong bao gio lech khi mot giao dich bi sua hoac xoa.
    initial_balance INTEGER NOT NULL DEFAULT 0,
    currency        TEXT    NOT NULL DEFAULT 'VND',
    icon            TEXT,
    is_default      BOOLEAN NOT NULL DEFAULT 0,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    deleted_at      TIMESTAMP,
    CONSTRAINT ck_wallet_type CHECK (type IN ('CASH', 'BANK', 'E_WALLET', 'CREDIT'))
);

-- "Unique khi chua xoa" phai la partial index, khong phai UNIQUE thuong
-- (03-DATA-MODEL.md §6, muc M-15).
CREATE UNIQUE INDEX idx_wallet_name ON wallet (name) WHERE deleted_at IS NULL;

CREATE TABLE category (
    id         TEXT    PRIMARY KEY,
    parent_id  TEXT    REFERENCES category (id) ON DELETE RESTRICT,
    name       TEXT    NOT NULL,
    type       TEXT    NOT NULL DEFAULT 'EXPENSE',
    icon       TEXT,
    color      TEXT    NOT NULL DEFAULT '#94a3b8',
    is_system  BOOLEAN NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CONSTRAINT ck_category_type           CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_category_not_own_parent CHECK (parent_id IS NULL OR parent_id != id)
);

-- COALESCE bat buoc: SQLite coi moi NULL la khac nhau trong unique index, nen neu de
-- `parent_id` tran thi hai danh muc goc trung ten van luot qua (03-DATA-MODEL.md §6, muc C-4).
CREATE UNIQUE INDEX idx_category_unique
    ON category (name, COALESCE(parent_id, ''), type) WHERE deleted_at IS NULL;

CREATE INDEX idx_category_parent ON category (parent_id);
CREATE INDEX idx_category_type   ON category (type, deleted_at);

-- `transaction` la tu khoa SQL nen moi tham chieu deu phai dat trong dau nhay kep.
-- Ten bang giu nguyen theo 03-DATA-MODEL.md §2.8; phia Java entity ten la MoneyTransaction.
CREATE TABLE "transaction" (
    id                TEXT    PRIMARY KEY,
    wallet_id         TEXT    NOT NULL REFERENCES wallet (id) ON DELETE RESTRICT,
    to_wallet_id      TEXT    REFERENCES wallet (id) ON DELETE RESTRICT,
    category_id       TEXT    REFERENCES category (id) ON DELETE RESTRICT,
    recurring_rule_id TEXT    REFERENCES recurring_rule (id) ON DELETE SET NULL,
    type              TEXT    NOT NULL DEFAULT 'EXPENSE',
    amount            INTEGER NOT NULL,
    note              TEXT,
    occurred_at       TIMESTAMP NOT NULL,
    source            TEXT    NOT NULL DEFAULT 'MANUAL',
    ai_confidence     REAL,
    import_hash       TEXT,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL,
    deleted_at        TIMESTAMP,
    CONSTRAINT ck_txn_amount   CHECK (amount > 0),
    CONSTRAINT ck_txn_type     CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    CONSTRAINT ck_txn_source   CHECK (source IN ('MANUAL', 'AI_PARSE', 'CSV_IMPORT', 'RECURRING')),
    -- Chuyen khoan bat buoc co vi dich va vi dich phai khac vi nguon (FR-FIN-05).
    CONSTRAINT ck_txn_transfer CHECK (type != 'TRANSFER' OR (to_wallet_id IS NOT NULL AND to_wallet_id != wallet_id)),
    -- Nguoc lai, thu/chi bat buoc co danh muc.
    CONSTRAINT ck_txn_category CHECK (type = 'TRANSFER' OR category_id IS NOT NULL)
);

-- Index dung nhieu nhat: danh sach giao dich luon sap xep theo thoi diem giam dan (NFR-PERF-03).
CREATE INDEX idx_txn_occurred    ON "transaction" (occurred_at DESC, deleted_at);
CREATE INDEX idx_txn_category    ON "transaction" (category_id, occurred_at);
CREATE INDEX idx_txn_wallet      ON "transaction" (wallet_id, occurred_at);
CREATE INDEX idx_txn_to_wallet   ON "transaction" (to_wallet_id, occurred_at);
CREATE INDEX idx_txn_import_hash ON "transaction" (import_hash);
CREATE INDEX idx_txn_recurring   ON "transaction" (recurring_rule_id);

-- Bang noi transaction <-> tag (03-DATA-MODEL.md §6, muc C-3).
CREATE TABLE transaction_tag (
    transaction_id TEXT NOT NULL REFERENCES "transaction" (id) ON DELETE CASCADE,
    tag_id         TEXT NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (transaction_id, tag_id)
);

CREATE INDEX idx_transaction_tag_tag ON transaction_tag (tag_id);

CREATE TABLE budget (
    id           TEXT    PRIMARY KEY,
    category_id  TEXT    NOT NULL REFERENCES category (id) ON DELETE RESTRICT,
    limit_amount INTEGER NOT NULL,
    period       TEXT    NOT NULL DEFAULT 'MONTHLY',
    start_date   TEXT    NOT NULL,
    is_active    BOOLEAN NOT NULL DEFAULT 1,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL,
    deleted_at   TIMESTAMP,
    CONSTRAINT ck_budget_limit  CHECK (limit_amount > 0),
    CONSTRAINT ck_budget_period CHECK (period IN ('WEEKLY', 'MONTHLY', 'YEARLY'))
);

-- Unique "(category_id, period) khi is_active = 1" — mot lan nua phai la partial index.
-- Ban ghi da soft delete cung phai duoc loai tru, neu khong nguoi dung khong tao lai duoc
-- ngan sach vua xoa.
CREATE UNIQUE INDEX idx_budget_active
    ON budget (category_id, period) WHERE is_active = 1 AND deleted_at IS NULL;

CREATE INDEX idx_budget_category ON budget (category_id, deleted_at);
