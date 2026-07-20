-- =============================================================
--  V1 — Create urls table
--
--  Flyway naming convention: V{version}__{description}.sql
--  Versions must be monotonically increasing.
--  Once applied to any environment a migration must NEVER be
--  modified — create a new version instead.
--
--  Design decisions:
--  - id is BIGSERIAL (auto-increment BIGINT) — gives 9.2 × 10^18
--    possible IDs, effectively unbounded.
--  - short_code VARCHAR(20) covers 7-char random codes and custom
--    aliases up to 20 chars. The UNIQUE constraint is backed by
--    an index automatically created by PostgreSQL.
--  - original_url is TEXT — PostgreSQL TEXT has no practical length
--    limit and avoids truncation for very long URLs.
--  - expires_at is nullable — NULL means the link never expires.
--  - click_count DEFAULT 0 — never NULL, incremented on each visit.
--  - is_active, is_custom_alias DEFAULT TRUE/FALSE — boolean flags.
--  - created_at/updated_at use TIMESTAMP WITHOUT TIME ZONE so
--    values are stored and returned in UTC. Application-layer code
--    (Spring) treats all LocalDateTime as UTC consistently.
--
--  Indexes:
--  - idx_urls_short_code (UNIQUE) — primary redirect lookup
--  - idx_urls_expires_at — used by scheduled cleanup query
--  - idx_urls_created_by — used by per-user listing queries
--  - idx_urls_is_active  — included in partial scans for active links
-- =============================================================

CREATE TABLE IF NOT EXISTS urls (
    id              BIGSERIAL       PRIMARY KEY,
    short_code      VARCHAR(20)     NOT NULL,
    original_url    TEXT            NOT NULL,
    title           VARCHAR(500),
    click_count     BIGINT          NOT NULL DEFAULT 0,
    expires_at      TIMESTAMP,
    is_custom_alias BOOLEAN         NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_by      VARCHAR(255),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_urls_short_code UNIQUE (short_code),
    CONSTRAINT chk_click_count_non_negative CHECK (click_count >= 0)
);

-- Index for primary redirect lookup (hot path)
CREATE UNIQUE INDEX IF NOT EXISTS idx_urls_short_code
    ON urls (short_code);

-- Index for scheduled expiry cleanup
CREATE INDEX IF NOT EXISTS idx_urls_expires_at
    ON urls (expires_at)
    WHERE expires_at IS NOT NULL;

-- Index for per-creator URL listing
CREATE INDEX IF NOT EXISTS idx_urls_created_by
    ON urls (created_by)
    WHERE created_by IS NOT NULL;

-- Partial index for active URLs only — keeps the index small
CREATE INDEX IF NOT EXISTS idx_urls_active
    ON urls (short_code)
    WHERE is_active = TRUE;

-- Composite index for the active + expiry check on the redirect path
CREATE INDEX IF NOT EXISTS idx_urls_active_expires
    ON urls (is_active, expires_at)
    WHERE is_active = TRUE;

COMMENT ON TABLE  urls                  IS 'Stores all shortened URL records';
COMMENT ON COLUMN urls.short_code       IS 'Unique 7-20 character code used in short links';
COMMENT ON COLUMN urls.original_url     IS 'Full destination URL';
COMMENT ON COLUMN urls.click_count      IS 'Total number of redirect events for this link';
COMMENT ON COLUMN urls.expires_at       IS 'Expiry timestamp; NULL means no expiry';
COMMENT ON COLUMN urls.is_custom_alias  IS 'TRUE if the short_code was provided by the user';
COMMENT ON COLUMN urls.is_active        IS 'FALSE indicates a soft-deleted link';
COMMENT ON COLUMN urls.created_by       IS 'Optional creator identifier (user ID, email, API key)';
