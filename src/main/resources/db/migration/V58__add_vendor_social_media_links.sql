CREATE TABLE vendor_social_media_links (
    id                 BIGSERIAL PRIMARY KEY,
    vendor_profile_id  BIGINT NOT NULL REFERENCES vendor_profiles(id) ON DELETE CASCADE,
    platform           VARCHAR(20) NOT NULL,
    url                VARCHAR(500) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vendor_social_media_links_vendor_profile_id ON vendor_social_media_links(vendor_profile_id);

-- One-time backfill: every vendor's existing Facebook Page URL becomes their
-- first social media link, so the storefront (which now reads from this
-- table instead of vendor_profiles.facebook_page_url directly) doesn't lose
-- that link for any vendor who already had one set before this feature
-- shipped. Going forward, UserService#becomeVendor/#updateSettings seed a
-- new vendor's very first Facebook link the same way, the moment they set
-- one with zero existing social media links - see those methods' comments.
INSERT INTO vendor_social_media_links (vendor_profile_id, platform, url, created_at, updated_at)
SELECT id, 'FACEBOOK', facebook_page_url, now(), now()
FROM vendor_profiles
WHERE facebook_page_url IS NOT NULL AND trim(facebook_page_url) != '';
