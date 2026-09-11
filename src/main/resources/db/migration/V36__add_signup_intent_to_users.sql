-- Permanent record of which door an account first walked through (planner
-- login vs. vendor sign-up), separate from role - role still flows
-- PLANNER -> VENDOR at becomeVendor time, but signup_intent never changes
-- once set. Backfilled from the current role: anyone already a VENDOR
-- clearly came in that door; everyone else is treated as PLANNER-origin
-- (the only accounts that could be mis-classified by this heuristic are any
-- vendor sign-ups currently abandoned mid-onboarding, which - at the time
-- of this migration - don't exist yet in production).
ALTER TABLE users ADD COLUMN signup_intent VARCHAR(20);

UPDATE users SET signup_intent = CASE WHEN role = 'VENDOR' THEN 'VENDOR' ELSE 'PLANNER' END;

ALTER TABLE users ALTER COLUMN signup_intent SET NOT NULL;
