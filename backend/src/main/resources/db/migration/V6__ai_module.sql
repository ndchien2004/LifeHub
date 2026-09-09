-- V6 — Phase 4: module AI (FR-AI-10).
--
-- Chi mot bang: nhat ky moi lan goi AI. Khong co bang nao luu ket qua parse, va do la co y —
-- AI khong bao gio ghi thang vao du lieu nghiep vu (AGENTS.md §3.4 muc 1). Ket qua parse song
-- trong bo nho cua mot request roi bien mat; ban ghi that chi ra doi khi user bam xac nhan tren
-- giao dien va frontend goi POST /transactions | /tasks | /events nhu binh thuong.
--
-- API key KHONG nam o day va cung khong nam o bang `setting` (03-DATA-MODEL.md §2.11): key duoc
-- Electron cat trong `safeStorage` cua he dieu hanh va truyen sang backend qua bien moi truong.

CREATE TABLE ai_parse_log (
    id           TEXT PRIMARY KEY,
    request_type TEXT    NOT NULL,
    -- Cau goc cua user. Luu de debug ket qua sai; xoa tu dong sau 90 ngay (03-DATA-MODEL.md §2.10).
    input_text   TEXT,
    -- JSON tho AI tra ve, da qua JsonSanitizer. Null khi lan goi that bai truoc do.
    output_json  TEXT,
    intent       TEXT,
    success      BOOLEAN NOT NULL DEFAULT 0,
    error_code   TEXT,
    latency_ms   INTEGER,
    token_input  INTEGER,
    token_output INTEGER,
    model        TEXT,
    created_at   TIMESTAMP NOT NULL,
    CONSTRAINT ck_ai_parse_log_request_type
        CHECK (request_type IN ('NL_PARSE', 'CATEGORY_SUGGEST', 'WEEKLY_INSIGHT')),
    CONSTRAINT ck_ai_parse_log_intent
        CHECK (intent IS NULL OR intent IN ('TASK', 'EVENT', 'TRANSACTION', 'UNKNOWN')),
    CONSTRAINT ck_ai_parse_log_error_code
        CHECK (error_code IS NULL OR error_code IN ('TIMEOUT', 'INVALID_JSON', 'AUTH', 'RATE_LIMIT', 'UNKNOWN'))
);

-- Man hinh xem log doc theo thu tu moi nhat truoc; job don dep quet theo cung cot.
CREATE INDEX idx_ai_parse_log_created ON ai_parse_log (created_at DESC);
