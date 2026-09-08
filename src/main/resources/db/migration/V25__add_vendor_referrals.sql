-- Vendor referral program: a vendor's referral_code is generated the first
-- time they onboard (see UserService#becomeVendor); vendor_referrals holds
-- one row per successful attribution, created at the referred vendor's
-- onboarding and flipped to CONVERTED the first time they actually pay
-- (PayPalWebhookService), then to COMMISSION_PAID manually by an admin.
ALTER TABLE vendor_profiles ADD COLUMN referral_code VARCHAR(20) UNIQUE;

CREATE TABLE vendor_referrals (
    id                BIGSERIAL PRIMARY KEY,
    referrer_user_id  BIGINT NOT NULL REFERENCES users(id),
    referred_user_id  BIGINT NOT NULL UNIQUE REFERENCES users(id),
    status            VARCHAR(20) NOT NULL,
    commission_amount NUMERIC(12,2),
    converted_at      TIMESTAMPTZ,
    paid_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vendor_referrals_referrer_user_id ON vendor_referrals(referrer_user_id);
