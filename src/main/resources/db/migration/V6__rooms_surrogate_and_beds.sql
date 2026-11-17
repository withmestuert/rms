-- =============================================================================
-- V3__rooms_surrogate_and_beds.sql: Rooms Surrogate PK and Beds Inventory
-- Implements DEC-001, PR-002, DEC-002: Surrogate BIGINT PK on rooms and atomic beds table
-- =============================================================================

-- 1. Add surrogate id to rooms with IMMEDIATE UNIQUE CONSTRAINT (PR-002 fix)
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS id BIGINT GENERATED ALWAYS AS IDENTITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_rooms_surrogate_id') THEN
        ALTER TABLE rooms ADD CONSTRAINT uk_rooms_surrogate_id UNIQUE (id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_rooms_property_number') THEN
        ALTER TABLE rooms ADD CONSTRAINT uk_rooms_property_number UNIQUE (property_id, room_no);
    END IF;
END $$;

-- 2. Create beds table
CREATE TABLE IF NOT EXISTS beds (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id BIGINT NOT NULL,
    bed_number VARCHAR(50) NOT NULL,
    base_rent INT,
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_beds_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    CONSTRAINT uk_beds_room_number UNIQUE (room_id, bed_number),
    CONSTRAINT chk_beds_status CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'RESERVED', 'MAINTENANCE'))
);

CREATE INDEX IF NOT EXISTS idx_beds_room_id ON beds(room_id);
CREATE INDEX IF NOT EXISTS idx_beds_status ON beds(status);

-- 3. Auto-generate physical bed slots for every room matching room occupancy
INSERT INTO beds (room_id, bed_number, base_rent, status)
SELECT 
    r.id,
    'Bed ' || seq.n,
    r.rent_per_month,
    'AVAILABLE'
FROM rooms r
CROSS JOIN LATERAL generate_series(1, COALESCE(r.occupancy, 1)) AS seq(n)
WHERE NOT EXISTS (
    SELECT 1 FROM beds b WHERE b.room_id = r.id AND b.bed_number = 'Bed ' || seq.n
);

-- 4. Mark beds as OCCUPIED for rooms that currently have active occupants
WITH room_active_occupants AS (
    SELECT r.id AS room_id, r.current_occupancy
    FROM rooms r
    WHERE r.current_occupancy > 0
),
ranked_beds AS (
    SELECT b.id AS bed_id,
           ROW_NUMBER() OVER (PARTITION BY b.room_id ORDER BY b.id) AS rn,
           rao.current_occupancy
    FROM beds b
    JOIN room_active_occupants rao ON b.room_id = rao.room_id
)
UPDATE beds
SET status = 'OCCUPIED'
WHERE id IN (
    SELECT bed_id FROM ranked_beds WHERE rn <= current_occupancy
);
