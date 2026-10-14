-- =============================================================================
-- V2__blocks_and_floors_hierarchy.sql: Blocks and Floors Hierarchy (PR-003, DEC-002, DEC-008)
-- Introduces blocks and floors and deterministically backfills existing rooms.
-- =============================================================================

-- 1. Create blocks table
CREATE TABLE IF NOT EXISTS blocks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    property_id BIGINT NOT NULL REFERENCES properties(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_blocks_property_code UNIQUE (property_id, code)
);
CREATE INDEX IF NOT EXISTS idx_blocks_property_id ON blocks(property_id);

-- 2. Create floors table
CREATE TABLE IF NOT EXISTS floors (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    block_id BIGINT NOT NULL REFERENCES blocks(id) ON DELETE RESTRICT,
    floor_number INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_floors_block_number UNIQUE (block_id, floor_number)
);
CREATE INDEX IF NOT EXISTS idx_floors_block_id ON floors(block_id);

-- 3. Dynamic Backfill: Default "Main Block" for every property
INSERT INTO blocks (property_id, name, code, status)
SELECT p.id, 'Main Block', 'BLK-' || COALESCE(p.code, CAST(p.id AS VARCHAR)), 'ACTIVE'
FROM properties p
WHERE NOT EXISTS (SELECT 1 FROM blocks b WHERE b.property_id = p.id);

-- 4. Dynamic Backfill: Extract numeric and special floors deterministically
-- Step 4a: Numeric floors
INSERT INTO floors (block_id, floor_number, name, status)
SELECT DISTINCT 
    b.id,
    CAST(r.floor AS INT),
    'Floor ' || r.floor,
    'ACTIVE'
FROM rooms r
JOIN blocks b ON b.property_id = r.property_id
WHERE r.floor ~ '^-?[0-9]+$'
  AND NOT EXISTS (
      SELECT 1 FROM floors f 
      WHERE f.block_id = b.id AND f.floor_number = CAST(r.floor AS INT)
  );

-- Step 4b: Ground / Basement / non-numeric text variations
INSERT INTO floors (block_id, floor_number, name, status)
SELECT DISTINCT 
    b.id,
    CASE 
        WHEN LOWER(TRIM(r.floor)) IN ('g', 'ground', 'ground floor') THEN 0
        WHEN LOWER(TRIM(r.floor)) IN ('b', 'basement') THEN -1
        ELSE 999
    END,
    CASE 
        WHEN LOWER(TRIM(r.floor)) IN ('g', 'ground', 'ground floor') THEN 'Ground Floor'
        WHEN LOWER(TRIM(r.floor)) IN ('b', 'basement') THEN 'Basement'
        ELSE 'Floor ' || COALESCE(r.floor, '1')
    END,
    'ACTIVE'
FROM rooms r
JOIN blocks b ON b.property_id = r.property_id
WHERE (r.floor IS NULL OR NOT (r.floor ~ '^-?[0-9]+$'))
  AND NOT EXISTS (
      SELECT 1 FROM floors f 
      WHERE f.block_id = b.id AND f.floor_number = CASE 
          WHEN LOWER(TRIM(r.floor)) IN ('g', 'ground', 'ground floor') THEN 0
          WHEN LOWER(TRIM(r.floor)) IN ('b', 'basement') THEN -1
          ELSE 999
      END
  );

-- 5. Expand rooms table with floor_id
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS floor_id BIGINT;

-- 6. Link rooms.floor_id deterministically
-- Numeric floors
UPDATE rooms r
SET floor_id = f.id
FROM floors f
JOIN blocks b ON f.block_id = b.id
WHERE r.property_id = b.property_id 
  AND r.floor ~ '^-?[0-9]+$'
  AND f.floor_number = CAST(r.floor AS INT)
  AND r.floor_id IS NULL;

-- Non-numeric / NULL floors
UPDATE rooms r
SET floor_id = f.id
FROM floors f
JOIN blocks b ON f.block_id = b.id
WHERE r.property_id = b.property_id 
  AND (r.floor IS NULL OR NOT (r.floor ~ '^-?[0-9]+$'))
  AND f.floor_number = CASE 
      WHEN LOWER(TRIM(r.floor)) IN ('g', 'ground', 'ground floor') THEN 0
      WHEN LOWER(TRIM(r.floor)) IN ('b', 'basement') THEN -1
      ELSE 999
  END
  AND r.floor_id IS NULL;

-- 7. Add foreign key on rooms.floor_id
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_rooms_floor') THEN
        ALTER TABLE rooms ADD CONSTRAINT fk_rooms_floor 
            FOREIGN KEY (floor_id) REFERENCES floors(id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_rooms_floor_id ON rooms(floor_id);
