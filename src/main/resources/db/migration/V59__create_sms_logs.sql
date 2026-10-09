CREATE TABLE sms_logs (
    id BIGSERIAL PRIMARY KEY,
    recipient_number VARCHAR(32) NOT NULL,
    message TEXT NOT NULL,
    trigger_type VARCHAR(64) NOT NULL,
    success BOOLEAN NOT NULL,
    error_message VARCHAR(500),
    sent_by_admin_email VARCHAR(255),
    related_entity_type VARCHAR(32),
    related_entity_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sms_logs_created_at ON sms_logs (created_at DESC);
