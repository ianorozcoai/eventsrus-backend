-- submitGcashPayment always reuses the vendor's single GCash-sourced row
-- (find-or-create by user+billingSource, see VendorSubscriptionService) -
-- there should never be more than one. Without this, two near-simultaneous
-- submissions (e.g. a double-click) can both pass the "no existing row"
-- check before either commits, each inserting its own row.
CREATE UNIQUE INDEX idx_vendor_subscriptions_one_gcash_row_per_user
    ON vendor_subscriptions (user_id)
    WHERE billing_source = 'GCASH';
