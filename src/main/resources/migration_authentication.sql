-- PostgreSQL, additive migration. Review and run through deployment tooling.
BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS owner_id bigint REFERENCES users(id);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS owner_id bigint REFERENCES users(id);
CREATE INDEX IF NOT EXISTS idx_properties_owner ON properties(owner_id);
CREATE INDEX IF NOT EXISTS idx_users_owner ON users(owner_id);
CREATE TABLE IF NOT EXISTS user_properties (
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    property_id bigint NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, property_id)
);
CREATE TABLE IF NOT EXISTS auth_sessions (
    token_hash varchar(255) PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at timestamp with time zone NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_user ON auth_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_expiry ON auth_sessions(expires_at);
COMMIT;
-- Owner assignment is deliberately manual. After verifying identities:
-- UPDATE properties SET owner_id = <verified_owner_id> WHERE id IN (<explicit_property_ids>);
-- Never assign properties solely because their owner_id is NULL.
