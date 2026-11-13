-- "Free PRO until N counted bookings" offer for recruited suppliers (promo code FREE3BOOKINGS).
-- A subscription with bookings_goal set stays free until the supplier has that many counted bookings
-- (a planner submitted payment) or until bookings_goal_deadline, whichever comes first.
-- Existing subscriptions are not affected: both columns are NULL = the normal date-based rules.
ALTER TABLE vendor_subscriptions ADD COLUMN bookings_goal INTEGER;
ALTER TABLE vendor_subscriptions ADD COLUMN bookings_goal_deadline TIMESTAMP WITH TIME ZONE;
