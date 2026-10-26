-- A renewal reminder now goes out on two channels (SMS and an in-app/push notification). Each channel is
-- tracked on its own, so a failed or disabled SMS never blocks the push, and vice versa.
ALTER TABLE subscription_reminders ADD COLUMN channel VARCHAR(8) NOT NULL DEFAULT 'SMS';
ALTER TABLE subscription_reminders DROP CONSTRAINT uq_subscription_reminders;
ALTER TABLE subscription_reminders
    ADD CONSTRAINT uq_subscription_reminders UNIQUE (user_id, access_end_date, days_before, channel);
