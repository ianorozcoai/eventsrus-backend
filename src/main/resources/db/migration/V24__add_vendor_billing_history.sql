-- One row per billing event (free-trial grant, initial payment, or renewal
-- payment) - see VendorBillingHistoryEntry for why this can't just be
-- derived from vendor_subscriptions itself (that row is mutated in place on
-- each renewal, so it can't answer "what did this vendor pay, and when").
CREATE TABLE vendor_billing_history (
    id                     BIGSERIAL PRIMARY KEY,
    vendor_subscription_id BIGINT NOT NULL REFERENCES vendor_subscriptions(id) ON DELETE CASCADE,
    plan                   VARCHAR(20) NOT NULL,
    billing_source         VARCHAR(20) NOT NULL,
    amount                 NUMERIC(12,2),
    currency               VARCHAR(10),
    paypal_transaction_id  VARCHAR(255),
    period_start           TIMESTAMPTZ NOT NULL,
    period_end             TIMESTAMPTZ NOT NULL,
    occurred_at            TIMESTAMPTZ NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_vendor_billing_history_paypal_transaction_id UNIQUE (paypal_transaction_id)
);
CREATE INDEX idx_vendor_billing_history_subscription_id ON vendor_billing_history(vendor_subscription_id);
