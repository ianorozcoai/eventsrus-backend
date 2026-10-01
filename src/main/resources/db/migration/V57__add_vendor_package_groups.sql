CREATE TABLE vendor_package_groups (
    id                 BIGSERIAL PRIMARY KEY,
    vendor_profile_id  BIGINT NOT NULL REFERENCES vendor_profiles(id) ON DELETE CASCADE,
    name               VARCHAR(50) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_vendor_package_groups_vendor_name UNIQUE (vendor_profile_id, name)
);
CREATE INDEX idx_vendor_package_groups_vendor_profile_id ON vendor_package_groups(vendor_profile_id);

CREATE TABLE vendor_package_group_memberships (
    package_id BIGINT NOT NULL REFERENCES vendor_packages(id) ON DELETE CASCADE,
    group_id   BIGINT NOT NULL REFERENCES vendor_package_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (package_id, group_id)
);
