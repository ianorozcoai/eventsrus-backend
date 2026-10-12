-- Bookmark-origin checklist items used to be labeled "Follow up with X";
-- EventService#addBookmarkChecklistItem now writes "X was bookmarked"
-- instead, but that only applies going forward - the label is plain text
-- captured at creation time, not derived, so any row created before this
-- change still carries the old wording. Backfill those here.
UPDATE event_checklist_items
SET label = regexp_replace(label, '^Follow up with (.+)$', '\1 was bookmarked')
WHERE label LIKE 'Follow up with %';
