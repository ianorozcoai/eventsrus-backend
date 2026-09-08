-- AdminInternalAuthController mints a real ADMIN-role JWT for
-- eventsrus-web's admin module without ever looking up a backing User row
-- (its own doc comment: "no real 'admin' User needs to exist for this") -
-- true for the vendor-verification endpoints it was built for, since those
-- never resolve "who is calling" to a database row. But SupportTicketService
-- (getMessages/reply/updateStatus) is shared with real vendors/planners
-- replying to their own tickets, and always resolves the caller via
-- userRepository.findByEmail(subject) - with no matching row, every one of
-- those calls failed for an admin acting through this bridge. Seeding a
-- real user whose email matches the bridge's token subject exactly fixes
-- this with zero code changes to that lookup - it just starts finding a row.
INSERT INTO users (google_id, email, first_name, last_name, role)
VALUES ('internal-admin-bridge', 'admin@eventsrus.internal', 'EventsRUs', 'Support', 'ADMIN')
ON CONFLICT (google_id) DO NOTHING;
