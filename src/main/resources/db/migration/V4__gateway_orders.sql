-- V4__gateway_orders.sql
-- Third-party payment gateway integration orders and authoritative tracking

CREATE TABLE IF NOT EXISTS gateway_orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL UNIQUE,
    invoice_id BIGINT NOT NULL,
    invoice_number VARCHAR(100) NOT NULL,
    tenant_uid VARCHAR(100) NOT NULL,
    property_id BIGINT,
    amount INT NOT NULL,
    amount_in_paise BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    gateway_provider VARCHAR(50) NOT NULL DEFAULT 'RAZORPAY',
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    razorpay_payment_id VARCHAR(100),
    razorpay_signature VARCHAR(255),
    payment_method VARCHAR(50),
    vpa VARCHAR(100),
    error_description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    paid_at TIMESTAMP,
    CONSTRAINT fk_gateway_orders_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_gateway_orders_property FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_gateway_orders_order_id ON gateway_orders(order_id);
CREATE INDEX IF NOT EXISTS idx_gateway_orders_invoice_id ON gateway_orders(invoice_id);
CREATE INDEX IF NOT EXISTS idx_gateway_orders_invoice_no ON gateway_orders(invoice_number);
CREATE INDEX IF NOT EXISTS idx_gateway_orders_tenant_uid ON gateway_orders(tenant_uid);
CREATE INDEX IF NOT EXISTS idx_gateway_orders_property_id ON gateway_orders(property_id);
