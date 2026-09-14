-- Migration: Add property_id identifier to all property-dependent tables

ALTER TABLE rooms ADD COLUMN IF NOT EXISTS property_id BIGINT;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS property_id BIGINT;
ALTER TABLE admissions ADD COLUMN IF NOT EXISTS property_id BIGINT;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS property_id BIGINT;
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS property_id BIGINT;

-- Foreign key constraints (optional / cascade or set null)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_rooms_property') THEN
        ALTER TABLE rooms ADD CONSTRAINT fk_rooms_property FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tenants_property') THEN
        ALTER TABLE tenants ADD CONSTRAINT fk_tenants_property FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_admissions_property') THEN
        ALTER TABLE admissions ADD CONSTRAINT fk_admissions_property FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_invoices_property') THEN
        ALTER TABLE invoices ADD CONSTRAINT fk_invoices_property FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ledger_property') THEN
        ALTER TABLE ledger_transactions ADD CONSTRAINT fk_ledger_property FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Indexes for efficient property-scoped querying
CREATE INDEX IF NOT EXISTS idx_rooms_property_id ON rooms(property_id);
CREATE INDEX IF NOT EXISTS idx_tenants_property_id ON tenants(property_id);
CREATE INDEX IF NOT EXISTS idx_admissions_property_id ON admissions(property_id);
CREATE INDEX IF NOT EXISTS idx_invoices_property_id ON invoices(property_id);
CREATE INDEX IF NOT EXISTS idx_ledger_property_id ON ledger_transactions(property_id);
