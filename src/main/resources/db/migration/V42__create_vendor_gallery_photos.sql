-- Standalone storefront photos, not tied to any package - lets a vendor
-- showcase sample work even when their packages themselves don't have much
-- to attach photos to. Combined with vendor_package_images to build the
-- storefront's single Gallery section (see VendorDirectoryService).
CREATE TABLE vendor_gallery_photos (
    id BIGSERIAL PRIMARY KEY,
    vendor_profile_id BIGINT NOT NULL REFERENCES vendor_profiles(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    caption VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vendor_gallery_photos_vendor_profile_id ON vendor_gallery_photos(vendor_profile_id);
