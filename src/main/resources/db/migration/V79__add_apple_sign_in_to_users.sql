-- Sign in with Apple: Apple users have no Google id, so google_id must be optional.
-- apple_id is Apple's stable per-app user identifier (the "sub" claim of the identity token).
-- apple_refresh_token (AES-GCM encrypted, see TokenEncryptionService) is only kept so the Apple
-- authorization can be revoked when the account is deleted; it stays NULL while Apple is not configured.
ALTER TABLE users ALTER COLUMN google_id DROP NOT NULL;
ALTER TABLE users ADD COLUMN apple_id VARCHAR(255);
ALTER TABLE users ADD CONSTRAINT uk_users_apple_id UNIQUE (apple_id);
ALTER TABLE users ADD COLUMN apple_refresh_token TEXT;
