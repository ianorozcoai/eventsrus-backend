-- A vendor's business-registration paperwork (DTI, SEC, Mayor's Permit,
-- Barangay Clearance, BIR, ...) - a business can reasonably have several of
-- these at once, which the old single vendor_profiles.business_permit_key
-- column couldn't represent. See VendorLegalDocumentService.
CREATE TABLE vendor_legal_documents (
    id                BIGSERIAL PRIMARY KEY,
    vendor_profile_id BIGINT NOT NULL REFERENCES vendor_profiles(id) ON DELETE CASCADE,
    document_type     VARCHAR(30) NOT NULL,
    label             VARCHAR(255),
    file_key          VARCHAR(500) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vendor_legal_documents_vendor_profile_id ON vendor_legal_documents(vendor_profile_id);

-- Carry forward any existing single permit upload before dropping the column
-- it lived in (none exist in this environment as of writing, verified via
-- psql, but this keeps the migration safe for any environment where they do).
INSERT INTO vendor_legal_documents (vendor_profile_id, document_type, label, file_key, created_at, updated_at)
SELECT id, 'OTHER', 'Business Permit', business_permit_key, now(), now()
FROM vendor_profiles
WHERE business_permit_key IS NOT NULL;

ALTER TABLE vendor_profiles DROP COLUMN business_permit_key;
