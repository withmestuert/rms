
-- Add bed reference to admissions
ALTER TABLE admissions
ADD COLUMN bed_id BIGINT;

-- Link admission to its assigned bed
ALTER TABLE admissions
ADD CONSTRAINT fk_admissions_bed
FOREIGN KEY (bed_id)
REFERENCES beds(id)
ON DELETE SET NULL;

-- Improve bed-based admission lookups
CREATE INDEX idx_admissions_bed_id
ON admissions(bed_id);