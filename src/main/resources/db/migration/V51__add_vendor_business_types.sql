CREATE TABLE vendor_business_types (
    vendor_profile_id BIGINT NOT NULL REFERENCES vendor_profiles(id),
    business_type VARCHAR(50) NOT NULL
);
CREATE INDEX idx_vendor_business_types_vendor_profile_id ON vendor_business_types(vendor_profile_id);
CREATE INDEX idx_vendor_business_types_business_type ON vendor_business_types(business_type);

INSERT INTO vendor_business_types (vendor_profile_id, business_type)
SELECT id, business_type FROM vendor_profiles WHERE business_type IS NOT NULL;
