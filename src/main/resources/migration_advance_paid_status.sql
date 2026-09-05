-- Step 1: Add advance_paid_status column safely if not exists
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS advance_paid_status VARCHAR(20);

-- Step 2 & 4: Backfill any existing rows where advance_paid_status is NULL to 'PENDING'
-- (Never infer PAID even if advance_paid > 0)
UPDATE tenants 
SET advance_paid_status = 'PENDING' 
WHERE advance_paid_status IS NULL;

-- Step 5: Enforce DEFAULT 'PENDING' and NOT NULL constraint for future records
ALTER TABLE tenants ALTER COLUMN advance_paid_status SET DEFAULT 'PENDING';
ALTER TABLE tenants ALTER COLUMN advance_paid_status SET NOT NULL;

-- Step 6: Verify the schema columns
SELECT column_name, data_type, is_nullable, column_default 
FROM information_schema.columns 
WHERE table_name = 'tenants' 
ORDER BY ordinal_position;

-- Step 6: Verify all tenant records (UID, advance_paid, advance_paid_status)
SELECT uid, name, room_no, advance_paid, advance_paid_status, standard_rent 
FROM tenants;
