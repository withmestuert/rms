-- =============================================================================
-- V0__baseline_schema.sql: Baseline Schema for RMS Database
-- Captures the 10 pre-redesign tables with IF NOT EXISTS for fresh installations.
-- =============================================================================

CREATE TABLE IF NOT EXISTS properties (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    code VARCHAR(255) UNIQUE,
    address VARCHAR(255),
    city VARCHAR(255),
    state VARCHAR(255),
    pincode VARCHAR(255),
    property_type VARCHAR(255),
    total_floors INT,
    total_rooms INT,
    contact_number VARCHAR(255),
    contact_email VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(255),
    deletion_reason VARCHAR(500),
    ttl_expires_at TIMESTAMP,
    archive_snapshot_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS rooms (
    room_no VARCHAR(255) NOT NULL PRIMARY KEY,
    available BOOLEAN,
    current_occupancy INT NOT NULL DEFAULT 0,
    floor VARCHAR(255),
    occupancy INT,
    property_id BIGINT,
    rent_per_month INT,
    reserved_capacity INT NOT NULL DEFAULT 0,
    room_type VARCHAR(255),
    vacate_date VARCHAR(255),
    vacate_status VARCHAR(255),
    vacating_resident VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS tenants (
    uid VARCHAR(255) NOT NULL PRIMARY KEY,
    aadhaar_no VARCHAR(255) UNIQUE,
    address VARCHAR(255),
    advance_paid INT,
    advance_paid_status VARCHAR(255),
    blood_group VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    date_of_birth DATE,
    documents_submitted VARCHAR(255),
    emergency_contact_name VARCHAR(255),
    emergency_contact_number VARCHAR(255),
    guardian_name VARCHAR(255),
    guardian_phone VARCHAR(255),
    guardian_relation VARCHAR(255),
    institution_or_company_name VARCHAR(255),
    institution_or_company_phone VARCHAR(255),
    institution_or_office_address VARCHAR(255),
    joining_date DATE,
    mobile_number VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    official_id VARCHAR(255),
    permanent_address VARCHAR(255),
    profile_photo_url VARCHAR(255),
    property_id BIGINT,
    role VARCHAR(255),
    room_no VARCHAR(255),
    standard_rent INT,
    status VARCHAR(255) NOT NULL,
    tenant_type VARCHAR(255),
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS admissions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admission_number VARCHAR(255) NOT NULL UNIQUE,
    confirmed_on TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    enrollment_date DATE NOT NULL,
    property_id BIGINT,
    remarks VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    vacated_on DATE,
    room_no VARCHAR(255) NOT NULL,
    tenant_uid VARCHAR(255) NOT NULL,
    CONSTRAINT admissions_status_check CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'VACATED'))
);

CREATE TABLE IF NOT EXISTS invoices (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    amount INT NOT NULL,
    paid_amount INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    due_date VARCHAR(255) NOT NULL,
    invoice_number VARCHAR(255) NOT NULL UNIQUE,
    month_year VARCHAR(255) NOT NULL,
    paid_on VARCHAR(255),
    payment_mode VARCHAR(255),
    property_id BIGINT,
    room_no VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    tenant_name VARCHAR(255) NOT NULL,
    tenant_uid VARCHAR(255) NOT NULL,
    transaction_ref VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS payments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    amount INT NOT NULL,
    invoice_id BIGINT NOT NULL,
    payment_date TIMESTAMP NOT NULL,
    payment_method VARCHAR(255) NOT NULL,
    property_id BIGINT,
    reference_number VARCHAR(255),
    tenant_uid VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS ledger_transactions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    amount INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    credit INT NOT NULL,
    debit INT NOT NULL,
    description VARCHAR(255) NOT NULL,
    invoice_id BIGINT,
    payment_id BIGINT,
    property_id BIGINT,
    reference_number VARCHAR(255),
    running_balance INT NOT NULL,
    tenant_uid VARCHAR(255),
    transaction_date TIMESTAMP NOT NULL,
    transaction_type VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS vacate_requests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    approved_by VARCHAR(255),
    approved_date VARCHAR(255),
    cancellation_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    deduction_charges DOUBLE PRECISION,
    deductions_breakdown TEXT,
    leave_date VARCHAR(255) NOT NULL,
    notice_period_days INT NOT NULL,
    property_id BIGINT,
    refund_amount DOUBLE PRECISION,
    refund_paid_date VARCHAR(255),
    refund_payment_mode VARCHAR(255),
    refund_status VARCHAR(255),
    refund_transaction_ref VARCHAR(255),
    rejection_reason VARCHAR(255),
    remarks VARCHAR(255),
    request_date VARCHAR(255) NOT NULL,
    request_id VARCHAR(255) NOT NULL UNIQUE,
    room_no VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    tenant_name VARCHAR(255) NOT NULL,
    tenant_uid VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    phone VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    property_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS property_archive_snapshots (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_property_id BIGINT NOT NULL,
    property_code VARCHAR(255),
    property_name VARCHAR(255),
    archived_at TIMESTAMP NOT NULL,
    archived_by VARCHAR(255),
    snapshot_json JSONB
);

CREATE TABLE IF NOT EXISTS migration_exceptions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phase VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_identifier VARCHAR(255) NOT NULL,
    error_message TEXT NOT NULL,
    payload JSONB,
    logged_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved BOOLEAN NOT NULL DEFAULT FALSE
);
