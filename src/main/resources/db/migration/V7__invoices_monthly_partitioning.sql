-- =============================================================================
-- V7__invoices_monthly_partitioning.sql
-- Converts 'invoices' table to a PostgreSQL Declarative Range-Partitioned Table
-- by 'created_at' (Monthly Partitions) for automated lifecycle & fast pruning.
-- =============================================================================

-- 1. Detach foreign key constraints referencing invoices(id)
ALTER TABLE IF EXISTS gateway_orders DROP CONSTRAINT IF EXISTS fk_gateway_orders_invoice;
ALTER TABLE IF EXISTS payments DROP CONSTRAINT IF EXISTS fk_payments_invoice;
ALTER TABLE IF EXISTS reminder_logs DROP CONSTRAINT IF EXISTS fk_reminder_logs_invoice;

-- Ensure child tables maintain high performance lookup indexes on invoice_id
CREATE INDEX IF NOT EXISTS idx_gateway_orders_invoice_id ON gateway_orders(invoice_id);
CREATE INDEX IF NOT EXISTS idx_payments_invoice_id ON payments(invoice_id);
CREATE INDEX IF NOT EXISTS idx_reminder_logs_invoice_id ON reminder_logs(invoice_id);

-- 2. Rename existing unpartitioned table to temporary staging table
ALTER TABLE IF EXISTS invoices RENAME TO invoices_old;

-- 3. Create the Declarative Partitioned Table
CREATE TABLE invoices (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    amount INT NOT NULL,
    paid_amount INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    due_date VARCHAR(255) NOT NULL,
    invoice_number VARCHAR(255) NOT NULL,
    month_year VARCHAR(255) NOT NULL,
    paid_on VARCHAR(255),
    payment_mode VARCHAR(255),
    property_id BIGINT,
    room_no VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    tenant_name VARCHAR(255) NOT NULL,
    tenant_uid VARCHAR(255) NOT NULL,
    transaction_ref VARCHAR(255),
    CONSTRAINT pk_invoices PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 4. Create Default Catch-All Partition (Guarantees zero write failures for any out-of-range dates)
CREATE TABLE IF NOT EXISTS invoices_default PARTITION OF invoices DEFAULT;

-- 5. Stored Procedure for dynamic on-demand monthly partition creation
CREATE OR REPLACE FUNCTION create_invoice_monthly_partition(p_date DATE)
RETURNS VOID AS '
DECLARE
    start_date DATE := date_trunc(''month'', p_date);
    end_date DATE := start_date + INTERVAL ''1 month'';
    part_name TEXT := ''invoices_y'' || to_char(start_date, ''YYYY'') || ''m'' || to_char(start_date, ''MM'');
    sql TEXT;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = ''public'' AND c.relname = part_name
    ) THEN
        sql := format(''CREATE TABLE IF NOT EXISTS %I PARTITION OF invoices FOR VALUES FROM (%L) TO (%L);'',
                      part_name, start_date, end_date);
        EXECUTE sql;
    END IF;
END;
' LANGUAGE plpgsql;

-- 6. Stored Procedure to auto-provision current month and future months
CREATE OR REPLACE FUNCTION auto_provision_invoice_partitions(months_ahead INT DEFAULT 6)
RETURNS VOID AS '
DECLARE
    current_m DATE := date_trunc(''month'', CURRENT_DATE);
    target_m DATE;
BEGIN
    -- Provision from 2025 up to current_m + months_ahead
    FOR i IN -12..months_ahead LOOP
        target_m := (current_m + (i || '' month'')::INTERVAL)::DATE;
        PERFORM create_invoice_monthly_partition(target_m);
    END LOOP;
END;
' LANGUAGE plpgsql;

-- 7. Pre-provision monthly partitions from 2025 through late 2027
SELECT auto_provision_invoice_partitions(14);

-- 8. Migrate existing records from staging table to partitioned table
INSERT INTO invoices (
    id, amount, paid_amount, created_at, due_date, invoice_number,
    month_year, paid_on, payment_mode, property_id, room_no,
    status, tenant_name, tenant_uid, transaction_ref
)
OVERRIDING SYSTEM VALUE
SELECT
    id, amount, paid_amount, created_at, due_date, invoice_number,
    month_year, paid_on, payment_mode, property_id, room_no,
    status, tenant_name, tenant_uid, transaction_ref
FROM invoices_old;

-- 9. Synchronize Identity sequence with maximum migrated ID
SELECT setval(pg_get_serial_sequence('invoices', 'id'), COALESCE(MAX(id), 1)) FROM invoices;

-- 10. Re-create essential query indexes (automatically propagated across all partitions)
CREATE INDEX IF NOT EXISTS idx_invoices_invoice_number ON invoices(invoice_number);
CREATE INDEX IF NOT EXISTS idx_invoices_tenant_month ON invoices(tenant_uid, month_year);
CREATE INDEX IF NOT EXISTS idx_invoices_property_id ON invoices(property_id);
CREATE INDEX IF NOT EXISTS idx_invoices_month_year ON invoices(month_year);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON invoices(status);
CREATE INDEX IF NOT EXISTS idx_invoices_due_date ON invoices(due_date);
CREATE INDEX IF NOT EXISTS idx_invoices_created_at ON invoices(created_at);

-- 11. Drop staging table after successful migration
DROP TABLE IF EXISTS invoices_old CASCADE;
