-- Tracks which Google OAuth client issued a connection's refresh token
-- (eventsrus-web's "Web application" client vs. eventsrus-ui's native
-- "Desktop app" client) - minting a fresh access token from a refresh
-- token must use the SAME client id/secret that originally issued it, or
-- Google rejects the refresh. Existing rows were all connected through the
-- web flow, hence the WEB default.
ALTER TABLE google_calendar_connections
    ADD COLUMN oauth_client VARCHAR(20) NOT NULL DEFAULT 'WEB';
