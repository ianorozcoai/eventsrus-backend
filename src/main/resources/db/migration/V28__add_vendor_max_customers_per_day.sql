-- Service Scope & Metrics (Account Settings tab 2) - how many separate
-- client bookings a vendor can take on in a single day (distinct from
-- max_guest_capacity, which is headcount per event, not bookings per day).
ALTER TABLE vendor_profiles ADD COLUMN max_customers_per_day INTEGER;
