-- Lets a planner opt into which AI-suggested supplier categories they
-- actually want to pursue for an event, plus an optional per-category
-- budget. Both default to "not yet decided" for every existing row.
ALTER TABLE event_suggested_vendors ADD COLUMN selected BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE event_suggested_vendors ADD COLUMN budget NUMERIC(12, 2);
