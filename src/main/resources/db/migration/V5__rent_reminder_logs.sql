-- V5__rent_reminder_logs.sql
-- Audit tracking and dispatch history for automated rent reminders

CREATE TABLE IF NOT EXISTS reminder_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    invoice_number VARCHAR(100) NOT NULL,
    tenant_uid VARCHAR(100) NOT NULL,
    tenant_name VARCHAR(150) NOT NULL,
    mobile_number VARCHAR(30),
    email VARCHAR(150),
    property_id BIGINT,
    channel VARCHAR(30) NOT NULL DEFAULT 'WHATSAPP',
    reminder_type VARCHAR(50) NOT NULL,
    days_offset INT NOT NULL DEFAULT 0,
    amount_due INT NOT NULL,
    message_body TEXT NOT NULL,
    payment_link VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SENT',
    error_message VARCHAR(500),
    sent_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_reminder_logs_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_reminder_logs_property FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_reminder_logs_invoice_id ON reminder_logs(invoice_id);
CREATE INDEX IF NOT EXISTS idx_reminder_logs_tenant_uid ON reminder_logs(tenant_uid);
CREATE INDEX IF NOT EXISTS idx_reminder_logs_property_id ON reminder_logs(property_id);
CREATE INDEX IF NOT EXISTS idx_reminder_logs_reminder_type ON reminder_logs(reminder_type);
CREATE INDEX IF NOT EXISTS idx_reminder_logs_sent_at ON reminder_logs(sent_at);
