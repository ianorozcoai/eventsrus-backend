-- A supplier can opt their public page out of search engines (Google). On by default.
ALTER TABLE vendor_profiles ADD COLUMN search_visible BOOLEAN NOT NULL DEFAULT TRUE;
