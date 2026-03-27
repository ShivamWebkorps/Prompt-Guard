-- ============================================
-- PromptGuard — Auto Schema Init
-- spring.sql.init.mode=always  → runs every restart
-- All statements are idempotent (IF NOT EXISTS)
-- ============================================

-- ── organizations table ──────────────────────
CREATE TABLE IF NOT EXISTS organizations (
    org_id   INTEGER      PRIMARY KEY,         -- e.g. 101, 102
    org_name VARCHAR(255) NOT NULL UNIQUE      -- e.g. 'Telecomm', 'Software'
);

-- ── users table ──────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    user_id      VARCHAR(100) PRIMARY KEY, -- Slug login (e.g. 'rohan-user')
    display_name VARCHAR(255),
    role         VARCHAR(20)  DEFAULT 'USER',
                              CHECK (role IN ('ADMIN', 'USER')),
    org_id       INTEGER      REFERENCES organizations(org_id) ON DELETE SET NULL,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── audit_logs table (includes browser_name) ─
CREATE TABLE IF NOT EXISTS audit_logs (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            VARCHAR(100) NOT NULL,
    tool               VARCHAR(100),
    browser_name       VARCHAR(50)  DEFAULT 'Unknown',
    original_prompt    TEXT,
    redacted_prompt    TEXT,
    highest_risk_type  VARCHAR(50)  DEFAULT 'NONE',
    risk_score         INTEGER      DEFAULT 0,
    risk_level         VARCHAR(20)  DEFAULT 'NONE',
    action             VARCHAR(20)  DEFAULT 'ALLOW',
    action_reason      TEXT,
    processing_time_ms BIGINT       DEFAULT 0,
    tokens_used        INTEGER      DEFAULT 0,
    tokens_saved       INTEGER      DEFAULT 0,
    cost_used          DOUBLE PRECISION DEFAULT 0.0,
    cost_saved         DOUBLE PRECISION DEFAULT 0.0,
    created_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Indexes ───────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_audit_user    ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_action  ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_risk    ON audit_logs(highest_risk_type);

-- ── User Keyword Policies Table ────────────────
CREATE TABLE IF NOT EXISTS user_keyword_policies (
    id            SERIAL       PRIMARY KEY,
    user_id       VARCHAR(100) NOT NULL, -- Parent user (e.g. )
    sub_user      VARCHAR(100) NOT NULL, -- Child user (e.g. user1)
    keyword_list  TEXT         NOT NULL, -- List of words to check
    allow_col     BOOLEAN      DEFAULT FALSE,
    redacted_col  BOOLEAN      DEFAULT FALSE,
    critical_col  BOOLEAN      DEFAULT FALSE,
    block_col     BOOLEAN      DEFAULT FALSE,
    prompt_col    TEXT                   -- Description / Context
);
