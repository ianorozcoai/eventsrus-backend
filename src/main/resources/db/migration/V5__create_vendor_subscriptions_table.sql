CREATE TABLE vendor_subscriptions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    start_date TIMESTAMPTZ NOT NULL,
    end_date   TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_vendor_subscriptions_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_vendor_subscriptions_user_id ON vendor_subscriptions(user_id);
