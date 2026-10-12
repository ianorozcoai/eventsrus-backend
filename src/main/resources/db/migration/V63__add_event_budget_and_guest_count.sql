-- New optional fields captured at event creation alongside the now-mandatory
-- name/eventType/eventDate/location. Nullable: existing rows (and anything
-- that predates this change) simply have no value for either.
ALTER TABLE events ADD COLUMN budget NUMERIC(12, 2);
ALTER TABLE events ADD COLUMN guest_count INTEGER;
