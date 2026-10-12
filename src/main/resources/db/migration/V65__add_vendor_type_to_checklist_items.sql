-- Lets a checklist item that came from a supplier-category selection
-- ("Book a catering vendor") carry back which category it's for, so the
-- planner can jump straight from the checklist to that category's matched
-- suppliers. Null for custom (planner-typed) checklist items.
ALTER TABLE event_checklist_items ADD COLUMN vendor_type VARCHAR(64);
