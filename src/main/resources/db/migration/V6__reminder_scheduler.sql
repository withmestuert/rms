-- V6__reminder_scheduler.sql
-- Automated Background Cron Scheduler configuration and run audit tracking

CREATE TABLE IF NOT EXISTS reminder_scheduler_config (
    id BIGINT PRIMARY KEY DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT true,
    cron_expression VARCHAR(50) NOT NULL DEFAULT '0 0 10 * * ?',
    morning_time VARCHAR(20) NOT NULL DEFAULT '10:00',
    target_categories VARCHAR(250) NOT NULL DEFAULT 'DUE_TODAY,OVERDUE_URGENT,FINAL_NOTICE',
    channel VARCHAR(50) NOT NULL DEFAULT 'WHATSAPP',
    cooldown_hours INT NOT NULL DEFAULT 24,
    last_run_at TIMESTAMP,
    last_run_status VARCHAR(50),
    last_run_summary VARCHAR(500),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO reminder_scheduler_config (id, enabled, cron_expression, morning_time, target_categories, channel, cooldown_hours, updated_at)
VALUES (1, true, '0 0 10 * * ?', '10:00', 'DUE_TODAY,OVERDUE_URGENT,FINAL_NOTICE', 'WHATSAPP', 24, NOW())
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS reminder_scheduler_runs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    triggered_by VARCHAR(50) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    invoices_evaluated INT NOT NULL DEFAULT 0,
    reminders_dispatched INT NOT NULL DEFAULT 0,
    reminders_skipped INT NOT NULL DEFAULT 0,
    summary_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reminder_scheduler_runs_created_at ON reminder_scheduler_runs(created_at DESC);
