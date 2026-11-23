ALTER TABLE property_archive_snapshots
    ADD COLUMN IF NOT EXISTS property_id BIGINT,
    ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS total_advance_held DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS total_residents INTEGER,
    ADD COLUMN IF NOT EXISTS total_revenue_potential DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS total_rooms INTEGER;