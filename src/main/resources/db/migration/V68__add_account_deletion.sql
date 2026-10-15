-- Account deletion (Google Play requirement): a user asks to delete, gets a
-- 30-day grace period (signing in again cancels it), then a daily job scrubs
-- the account in place. The users row stays as an anonymous shell because
-- bookings, quotations, billing history etc. reference users(id).
ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX idx_users_deletion_requested_at ON users (deletion_requested_at)
    WHERE deleted_at IS NULL AND deletion_requested_at IS NOT NULL;
