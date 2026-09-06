DELETE FROM users WHERE username = 'testuser';

ALTER TABLE users DROP CONSTRAINT users_role_check;
UPDATE users SET role = 'PLANNER' WHERE role = 'USER';
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'PLANNER';
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('PLANNER', 'VENDOR', 'ADMIN'));

ALTER TABLE users ADD COLUMN google_id VARCHAR(255) NOT NULL;
ALTER TABLE users ADD CONSTRAINT uk_users_google_id UNIQUE (google_id);

ALTER TABLE users DROP COLUMN username;
ALTER TABLE users DROP COLUMN password_hash;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

ALTER TABLE users ADD COLUMN first_name    VARCHAR(100);
ALTER TABLE users ADD COLUMN last_name     VARCHAR(100);
ALTER TABLE users ADD COLUMN mobile_number VARCHAR(20);
ALTER TABLE users ADD COLUMN business_name VARCHAR(255);
ALTER TABLE users ADD COLUMN business_type VARCHAR(20)
    CHECK (business_type IS NULL OR business_type IN
        ('CATERING', 'PHOTOGRAPHY', 'VENUE', 'ENTERTAINMENT', 'DECORATION', 'OTHER'));
