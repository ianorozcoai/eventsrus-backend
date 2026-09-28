CREATE TABLE vendor_image_tags (
    id                 BIGSERIAL PRIMARY KEY,
    vendor_profile_id  BIGINT NOT NULL REFERENCES vendor_profiles(id) ON DELETE CASCADE,
    name               VARCHAR(50) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_vendor_image_tags_vendor_name UNIQUE (vendor_profile_id, name)
);
CREATE INDEX idx_vendor_image_tags_vendor_profile_id ON vendor_image_tags(vendor_profile_id);

CREATE TABLE vendor_gallery_photo_tags (
    gallery_photo_id BIGINT NOT NULL REFERENCES vendor_gallery_photos(id) ON DELETE CASCADE,
    tag_id           BIGINT NOT NULL REFERENCES vendor_image_tags(id) ON DELETE CASCADE,
    PRIMARY KEY (gallery_photo_id, tag_id)
);

CREATE TABLE vendor_package_image_tags (
    package_image_id BIGINT NOT NULL REFERENCES vendor_package_images(id) ON DELETE CASCADE,
    tag_id           BIGINT NOT NULL REFERENCES vendor_image_tags(id) ON DELETE CASCADE,
    PRIMARY KEY (package_image_id, tag_id)
);
