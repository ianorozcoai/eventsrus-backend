CREATE TABLE vendor_operating_areas (
    vendor_profile_id BIGINT NOT NULL REFERENCES vendor_profiles(id),
    area VARCHAR(100) NOT NULL
);
CREATE INDEX idx_vendor_operating_areas_vendor_profile_id ON vendor_operating_areas(vendor_profile_id);
CREATE INDEX idx_vendor_operating_areas_area ON vendor_operating_areas(area);
