-- PostgreSQL. Apply migration_authentication.sql first when upgrading an existing database.
BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until timestamp with time zone;
ALTER TABLE users ADD COLUMN IF NOT EXISTS authentication_version bigint NOT NULL DEFAULT 0;
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS authentication_version bigint NOT NULL DEFAULT 0;
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS owner_id bigint;
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS owner_authentication_version bigint;
CREATE TABLE IF NOT EXISTS root_bootstrap (id bigint PRIMARY KEY CHECK (id = 1));
CREATE UNIQUE INDEX IF NOT EXISTS idx_single_root_account ON users(role) WHERE role = 'ROOT';
-- Sessions predating credential versioning cannot prove their owner version.
DELETE FROM auth_sessions WHERE user_id IN (SELECT id FROM users WHERE owner_id IS NOT NULL)
    AND owner_authentication_version IS NULL;
COMMIT;
