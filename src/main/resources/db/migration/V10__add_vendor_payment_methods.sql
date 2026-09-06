ALTER TABLE vendor_profiles ADD COLUMN payment_instructions TEXT;

CREATE TABLE vendor_payment_methods (
    id BIGSERIAL PRIMARY KEY,
    vendor_profile_id BIGINT NOT NULL REFERENCES vendor_profiles(id),
    label VARCHAR(255) NOT NULL,
    qr_image_url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vendor_payment_methods_vendor_profile_id ON vendor_payment_methods(vendor_profile_id);

-- Vendor Terms & Agreement acceptance, recorded on becomeVendor.
ALTER TABLE users ADD COLUMN terms_accepted_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN terms_version VARCHAR(50);
