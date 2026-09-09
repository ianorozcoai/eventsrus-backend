-- Admin approval for vendor payment methods is disabled for now (no review
-- screen exists yet - see VendorPaymentMethodService#create, which now
-- auto-approves new uploads). One-time backfill so anything a vendor
-- already uploaded before this change - stuck PENDING and invisible on
-- their storefront - becomes visible too, instead of only new uploads.
UPDATE vendor_payment_methods SET status = 'APPROVED' WHERE status = 'PENDING';
