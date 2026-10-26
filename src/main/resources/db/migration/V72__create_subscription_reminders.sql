-- One row per renewal-reminder SMS already sent (14, 7 and 3 days before access ends), so the daily job
-- never sends the same reminder twice. Keyed by the access end date: when a supplier pays, the end
-- date moves and the reminders start again for the new date.
CREATE TABLE subscription_reminders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    access_end_date DATE NOT NULL,
    days_before INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_reminders UNIQUE (user_id, access_end_date, days_before)
);
