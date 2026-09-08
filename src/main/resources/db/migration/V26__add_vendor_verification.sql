-- Manual admin verification for vendors. Uploading an ID card, selfie, and
-- legal documents at onboarding no longer implies the vendor is verified -
-- that was previously derived automatically (id_card_key/selfie_key both
-- present), which meant every onboarded vendor showed a "Verified Vendor"
-- badge on their storefront with nobody at EventsRUs ever having looked at
-- the documents. An admin now has to review the submitted documents and
-- explicitly mark the vendor verified (see AdminVendorController) before
-- that badge appears.
ALTER TABLE vendor_profiles ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE vendor_profiles ADD COLUMN verified_at TIMESTAMPTZ;
-- Free-text, not a foreign key - the admin module (eventsrus-web) has its
-- own separate, in-memory account store (see AdminAccountService there),
-- not a real backend-side admin User per account, so this just records
-- whichever admin username performed the action for a basic audit trail.
ALTER TABLE vendor_profiles ADD COLUMN verified_by_admin VARCHAR(100);
