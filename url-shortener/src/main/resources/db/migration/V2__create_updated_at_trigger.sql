-- =============================================================
--  V2 — Auto-update updated_at column via PostgreSQL trigger
--
--  Why a trigger instead of relying on JPA @UpdateTimestamp?
--  - Any UPDATE issued directly via SQL (e.g. the JPQL
--    incrementClickCount query) bypasses JPA lifecycle hooks.
--  - The trigger guarantees updated_at is always current
--    regardless of how the row was modified.
-- =============================================================

-- Function used by all update triggers in this database
CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Attach the trigger to the urls table
CREATE TRIGGER trg_urls_updated_at
    BEFORE UPDATE ON urls
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
