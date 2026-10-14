-- =============================================================================
-- V1__property_safeguards.sql: Property Safeguards & Physical Foreign Keys
-- Enforces referential integrity with ON DELETE RESTRICT (DEC-006, PR-001)
-- =============================================================================

-- 1. Create indexes on property_id to prevent table locks during constraint checks
CREATE INDEX IF NOT EXISTS idx_rooms_property_id ON rooms(property_id);
CREATE INDEX IF NOT EXISTS idx_tenants_property_id ON tenants(property_id);
CREATE INDEX IF NOT EXISTS idx_admissions_property_id ON admissions(property_id);
CREATE INDEX IF NOT EXISTS idx_invoices_property_id ON invoices(property_id);
CREATE INDEX IF NOT EXISTS idx_payments_property_id ON payments(property_id);
CREATE INDEX IF NOT EXISTS idx_ledger_property_id ON ledger_transactions(property_id);
CREATE INDEX IF NOT EXISTS idx_vacate_property_id ON vacate_requests(property_id);

-- 2. Enforce physical foreign keys with ON DELETE RESTRICT
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_rooms_property') THEN
        ALTER TABLE rooms ADD CONSTRAINT fk_rooms_property 
            FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tenants_property') THEN
        ALTER TABLE tenants ADD CONSTRAINT fk_tenants_property 
            FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_admissions_property') THEN
        ALTER TABLE admissions ADD CONSTRAINT fk_admissions_property 
            FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_invoices_property') THEN
        ALTER TABLE invoices ADD CONSTRAINT fk_invoices_property 
            FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_payments_property') THEN
        ALTER TABLE payments ADD CONSTRAINT fk_payments_property 
            FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_payments_invoice') THEN
        ALTER TABLE payments ADD CONSTRAINT fk_payments_invoice 
            FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ledger_property') THEN
        ALTER TABLE ledger_transactions ADD CONSTRAINT fk_ledger_property 
            FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_vacate_property') THEN
        ALTER TABLE vacate_requests ADD CONSTRAINT fk_vacate_property 
            FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE RESTRICT;
    END IF;
END $$;
