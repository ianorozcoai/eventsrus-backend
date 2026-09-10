-- =============================================================================
--  unseed-vendors.sql   - removes everything seed-500-vendors.sql created
-- =============================================================================
--  Safe: only touches rows whose owning user has google_id LIKE 'seedv-%'
--  (and email '%@seed.eventsrus.test'). Real vendors are never matched.
--
--  USAGE (local only):
--    PGPASSWORD=postgres psql -h localhost -U postgres -d eventsrus \
--        -f scripts/unseed-vendors.sql
-- =============================================================================

BEGIN;

CREATE TEMP TABLE _seed_users ON COMMIT DROP AS
    SELECT id FROM users WHERE google_id LIKE 'seedv-%' OR email LIKE '%@seed.eventsrus.test';

CREATE TEMP TABLE _seed_profiles ON COMMIT DROP AS
    SELECT id FROM vendor_profiles WHERE user_id IN (SELECT id FROM _seed_users);

CREATE TEMP TABLE _seed_subs ON COMMIT DROP AS
    SELECT id FROM vendor_subscriptions WHERE user_id IN (SELECT id FROM _seed_users);

-- children of vendor_packages
DELETE FROM vendor_package_images
    WHERE vendor_package_id IN (SELECT id FROM vendor_packages WHERE vendor_profile_id IN (SELECT id FROM _seed_profiles));
DELETE FROM vendor_packages           WHERE vendor_profile_id IN (SELECT id FROM _seed_profiles);

-- child collections of vendor_profiles
DELETE FROM vendor_catered_event_types WHERE vendor_profile_id IN (SELECT id FROM _seed_profiles);
DELETE FROM vendor_operating_areas     WHERE vendor_profile_id IN (SELECT id FROM _seed_profiles);
DELETE FROM vendor_payment_methods     WHERE vendor_profile_id IN (SELECT id FROM _seed_profiles);
DELETE FROM vendor_legal_documents     WHERE vendor_profile_id IN (SELECT id FROM _seed_profiles);
DELETE FROM event_suggested_vendors    WHERE vendor_profile_id IN (SELECT id FROM _seed_profiles);

-- subscription history + the subscriptions
DELETE FROM vendor_billing_history     WHERE vendor_subscription_id IN (SELECT id FROM _seed_subs);
DELETE FROM vendor_subscription_events WHERE vendor_subscription_id IN (SELECT id FROM _seed_subs);
DELETE FROM vendor_subscriptions       WHERE id IN (SELECT id FROM _seed_subs);

-- anything else keyed on the seeded users
DELETE FROM vendor_referrals WHERE referred_user_id IN (SELECT id FROM _seed_users)
                                OR referrer_user_id IN (SELECT id FROM _seed_users);
DELETE FROM notifications    WHERE recipient_user_id IN (SELECT id FROM _seed_users);

DELETE FROM vendor_profiles WHERE id IN (SELECT id FROM _seed_profiles);
DELETE FROM users           WHERE id IN (SELECT id FROM _seed_users);

DO $$
BEGIN
    RAISE NOTICE 'Remaining seed users: %', (SELECT count(*) FROM users WHERE google_id LIKE 'seedv-%');
END
$$;

COMMIT;
