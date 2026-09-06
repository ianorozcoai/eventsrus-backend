CREATE TABLE vendor_catered_event_types (
    vendor_profile_id BIGINT NOT NULL REFERENCES vendor_profiles(id),
    event_type VARCHAR(30) NOT NULL
);
CREATE INDEX idx_vendor_catered_event_types_vendor_profile_id ON vendor_catered_event_types(vendor_profile_id);
