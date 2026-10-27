-- One-time "Welcome to EventsRUs - you have a free trial" message for a supplier who has just signed up.
-- Set when onboarding starts their free trial, cleared when they answer it (Subscribe now / Continue with the
-- free trial). Existing suppliers are not affected: the default is false.
ALTER TABLE users ADD COLUMN trial_welcome_pending BOOLEAN NOT NULL DEFAULT FALSE;
