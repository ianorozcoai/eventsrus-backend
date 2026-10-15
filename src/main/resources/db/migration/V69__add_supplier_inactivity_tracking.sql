-- Lapsed-supplier cleanup (see InactiveSupplierCleanupService): a supplier with no live
-- subscription and no sign-in for 12 months loses their stored ID/selfie files; at 24 months
-- they get an email warning and, 30 days later, the same deletion as "Delete account".
ALTER TABLE users ADD COLUMN inactivity_warning_sent_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN id_files_purged_at TIMESTAMPTZ;
