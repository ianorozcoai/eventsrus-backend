-- Marks accounts created directly by the team (e.g. demo/placeholder
-- vendors seeded to make the marketplace look populated before real
-- vendors sign up) so they can be found and bulk-removed later without
-- guessing from email/name patterns. Defaults false for every real,
-- organically-signed-up account.
ALTER TABLE users
    ADD COLUMN fake_account BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_users_fake_account ON users (fake_account) WHERE fake_account = true;
