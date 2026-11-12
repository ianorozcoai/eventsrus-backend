-- Manual "finish your sign-up" email to a supplier who never completed onboarding (sent from the admin
-- Incomplete Sign-ups tab). Tracks when it last went out and how many have been sent, to cap repeats.
ALTER TABLE users ADD COLUMN signup_reminder_last_sent_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN signup_reminder_count INTEGER NOT NULL DEFAULT 0;
