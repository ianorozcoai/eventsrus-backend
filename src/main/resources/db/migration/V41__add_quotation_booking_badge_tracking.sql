-- Powers the "unseen activity" badges on the vendor nav (Quotations/Bookings)
-- and the planner's per-event Quotations/Bookings tabs: a user's own
-- last-viewed timestamp per category, compared against the quotation/
-- booking's updated_at, tells us what's new since they last looked -
-- regardless of which side's action caused the change (their own included),
-- which a purely notification-driven count can't do (nobody notifies
-- themselves of their own action).
ALTER TABLE users
    ADD COLUMN quotations_badge_seen_at TIMESTAMPTZ,
    ADD COLUMN bookings_badge_seen_at TIMESTAMPTZ;

-- A planner's "seen" state is per event (each event has its own Quotations/
-- Bookings tabs), so it can't live as a single column on users the way the
-- vendor's nav-wide badge can.
CREATE TABLE event_tab_views (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    quotations_seen_at TIMESTAMPTZ,
    bookings_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_event_tab_views_user_event UNIQUE (user_id, event_id)
);
