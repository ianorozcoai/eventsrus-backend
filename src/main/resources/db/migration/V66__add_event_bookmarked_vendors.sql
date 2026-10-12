-- A planner can bookmark a specific supplier for an event while browsing a
-- category (storefront "Bookmark" button) - distinct from just checking a
-- category, this names an actual vendor the planner wants to follow up
-- with. One row per (event, vendor); bookmarking also creates a checklist
-- item (see EventService#addBookmarkChecklistItem), so this column lets
-- that checklist item link straight to the vendor's storefront.
CREATE TABLE event_bookmarked_vendors (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    vendor_profile_id BIGINT NOT NULL REFERENCES vendor_profiles(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (event_id, vendor_profile_id)
);
CREATE INDEX idx_event_bookmarked_vendors_event_id ON event_bookmarked_vendors(event_id);

ALTER TABLE event_checklist_items ADD COLUMN vendor_profile_id BIGINT REFERENCES vendor_profiles(id);
