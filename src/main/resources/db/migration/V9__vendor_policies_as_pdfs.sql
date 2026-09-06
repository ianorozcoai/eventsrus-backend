-- Account Settings tab 3 (Marketplace Escrow & Policies) drops
-- retainer_percentage, and cancellation_policy/refund_terms become
-- vendor-uploaded PDFs (stored in the public S3 bucket) instead of free
-- text, so they hold a public URL now rather than raw policy text.
ALTER TABLE vendor_profiles DROP COLUMN retainer_percentage;
ALTER TABLE vendor_profiles RENAME COLUMN cancellation_policy TO cancellation_policy_url;
ALTER TABLE vendor_profiles RENAME COLUMN refund_terms TO refund_terms_url;
