-- Real photos per package - shown on the package card in Manage Packages
-- (edit mode) and, combined across every one of a vendor's packages, as the
-- storefront's Gallery section (see VendorPackageImageService,
-- VendorDirectoryService#getPublicProfile). Public bucket, direct URL (no
-- presigning) - marketing photos, same treatment as the vendor logo, not a
-- privacy-sensitive document like the legal-document uploads.
CREATE TABLE vendor_package_images (
    id                 BIGSERIAL PRIMARY KEY,
    vendor_package_id  BIGINT NOT NULL REFERENCES vendor_packages(id) ON DELETE CASCADE,
    image_url          VARCHAR(500) NOT NULL,
    caption            VARCHAR(200),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vendor_package_images_package_id ON vendor_package_images(vendor_package_id);
