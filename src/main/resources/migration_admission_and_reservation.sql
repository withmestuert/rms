-- ==============================================================================
-- Migration: Add Room Reservation fields and Admissions Table
-- ==============================================================================

-- 1. Extend rooms table with current_occupancy and reserved_capacity
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS current_occupancy INT DEFAULT 0;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS reserved_capacity INT DEFAULT 0;

UPDATE rooms SET current_occupancy = 0 WHERE current_occupancy IS NULL;
UPDATE rooms SET reserved_capacity = 0 WHERE reserved_capacity IS NULL;

ALTER TABLE rooms ALTER COLUMN current_occupancy SET DEFAULT 0;
ALTER TABLE rooms ALTER COLUMN current_occupancy SET NOT NULL;

ALTER TABLE rooms ALTER COLUMN reserved_capacity SET DEFAULT 0;
ALTER TABLE rooms ALTER COLUMN reserved_capacity SET NOT NULL;

-- Backfill current_occupancy from active tenants
UPDATE rooms r 
SET current_occupancy = COALESCE((SELECT COUNT(*) FROM tenants t WHERE t.room_no = r.room_no), 0);

-- Recalculate room availability flag based on capacity
UPDATE rooms 
SET available = ((current_occupancy + reserved_capacity) < occupancy);

-- 2. Create admissions table
CREATE TABLE IF NOT EXISTS admissions (
    id BIGSERIAL PRIMARY KEY,
    admission_number VARCHAR(30) UNIQUE NOT NULL,
    tenant_uid VARCHAR NOT NULL,
    room_no VARCHAR NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    enrollment_date DATE NOT NULL,
    remarks TEXT,
    confirmed_on TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_admissions_tenant FOREIGN KEY (tenant_uid) REFERENCES tenants (uid),
    CONSTRAINT fk_admissions_room FOREIGN KEY (room_no) REFERENCES rooms (room_no)
);

-- 3. Create sequence for collision-safe business admission numbers
CREATE SEQUENCE IF NOT EXISTS admission_seq START WITH 1001;
