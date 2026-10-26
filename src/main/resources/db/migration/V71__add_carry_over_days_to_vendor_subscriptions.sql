-- Paying early: days a supplier still had left on their previous plan when a new paid plan started.
-- Added on top of the new plan's own end date (see VendorPlanService), so nothing paid for is lost.
ALTER TABLE vendor_subscriptions ADD COLUMN carry_over_days INTEGER NOT NULL DEFAULT 0;
