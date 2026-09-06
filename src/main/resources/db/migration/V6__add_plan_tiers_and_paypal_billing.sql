ALTER TABLE vendor_subscriptions RENAME COLUMN start_date TO current_period_start;
ALTER TABLE vendor_subscriptions RENAME COLUMN end_date TO current_period_end;
ALTER TABLE vendor_subscriptions ALTER COLUMN current_period_start DROP NOT NULL;
ALTER TABLE vendor_subscriptions ALTER COLUMN current_period_end DROP NOT NULL;

ALTER TABLE vendor_subscriptions ADD COLUMN plan VARCHAR(20) NOT NULL DEFAULT 'PRO';
ALTER TABLE vendor_subscriptions ADD COLUMN billing_cycle VARCHAR(20);
ALTER TABLE vendor_subscriptions ADD COLUMN billing_source VARCHAR(20) NOT NULL DEFAULT 'FREE_GRANT';
ALTER TABLE vendor_subscriptions ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE vendor_subscriptions ADD COLUMN paypal_subscription_id VARCHAR(255);
ALTER TABLE vendor_subscriptions ADD COLUMN paypal_plan_id VARCHAR(255);

ALTER TABLE vendor_subscriptions ALTER COLUMN plan DROP DEFAULT;
ALTER TABLE vendor_subscriptions ALTER COLUMN billing_source DROP DEFAULT;
ALTER TABLE vendor_subscriptions ALTER COLUMN status DROP DEFAULT;

ALTER TABLE vendor_subscriptions ADD CONSTRAINT uq_vendor_subscriptions_paypal_subscription_id UNIQUE (paypal_subscription_id);
CREATE INDEX idx_vendor_subscriptions_status ON vendor_subscriptions(status);

CREATE TABLE vendor_subscription_events (
    id                     BIGSERIAL PRIMARY KEY,
    vendor_subscription_id BIGINT,
    paypal_event_id        VARCHAR(255) NOT NULL,
    event_type             VARCHAR(100) NOT NULL,
    payload                TEXT,
    occurred_at            TIMESTAMPTZ NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_vendor_subscription_events_paypal_event_id UNIQUE (paypal_event_id),
    CONSTRAINT fk_vendor_subscription_events_subscription FOREIGN KEY (vendor_subscription_id)
        REFERENCES vendor_subscriptions(id)
);

CREATE INDEX idx_vendor_subscription_events_subscription_id ON vendor_subscription_events(vendor_subscription_id);
