-- "Top Vendor" - an admin-set spotlight flag, separate from "verified"
-- (identity/documents checked). A vendor can be one, both, or neither.
-- Shown as a badge in the planner's Recommended Suppliers list.
ALTER TABLE vendor_profiles ADD COLUMN top_vendor BOOLEAN NOT NULL DEFAULT FALSE;
