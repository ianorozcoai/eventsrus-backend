-- Resets a vendor's manual verification back to "pending review" - useful
-- while testing the onboarding -> admin review flow (see AdminVendorController
-- / VendorDirectoryService#getPublicProfile), since onboarding itself never
-- sets verified=true - only an admin clicking "Mark as Verified" in
-- eventsrus-web's /admin/verifications does.
--
-- Usage: psql -h <host> -U <user> -d eventsrus -f reset-vendor-verification.sql
-- (edit the slug below first, or swap the WHERE clause for user_id/contact_email)

UPDATE vendor_profiles
SET verified = FALSE,
    verified_at = NULL,
    verified_by_admin = NULL
WHERE slug = 'blossom-bloom-florals';

-- To reset EVERY vendor at once instead (e.g. resetting a whole test pass):
-- UPDATE vendor_profiles SET verified = FALSE, verified_at = NULL, verified_by_admin = NULL;

-- To check the result:
-- SELECT id, business_name, slug, verified, verified_at, verified_by_admin FROM vendor_profiles;
