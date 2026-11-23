
-- Add columns required by the current Payment entity

ALTER TABLE payments
    ADD COLUMN payment_reference VARCHAR(255),
    ADD COLUMN payment_mode VARCHAR(255),
    ADD COLUMN transaction_reference VARCHAR(255),
    ADD COLUMN idempotency_key VARCHAR(255),
    ADD COLUMN status VARCHAR(255),
    ADD COLUMN created_at TIMESTAMP;

-- Preserve existing payment dates
UPDATE payments
SET created_at = payment_date
WHERE created_at IS NULL;

-- Provide a default for existing records
UPDATE payments
SET status = 'SUCCESS'
WHERE status IS NULL;

-- Add constraints after populating existing records
ALTER TABLE payments
    ALTER COLUMN payment_reference SET NOT NULL,
    ALTER COLUMN idempotency_key SET NOT NULL,
    ALTER COLUMN payment_mode SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL;

-- Add unique constraints matching the entity
ALTER TABLE payments
    ADD CONSTRAINT uk_payments_idempotency_key
        UNIQUE (idempotency_key),
    ADD CONSTRAINT uk_payments_payment_ref
        UNIQUE (payment_reference);

-- Remove legacy columns after confirming they are no longer used
-- Do not execute these until old application references are checked.
-- ALTER TABLE payments DROP COLUMN payment_date;
-- ALTER TABLE payments DROP COLUMN payment_method;
-- ALTER TABLE payments DROP COLUMN reference_number;
