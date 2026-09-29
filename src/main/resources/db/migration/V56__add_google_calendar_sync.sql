CREATE TABLE google_calendar_connections (
    id                       BIGSERIAL PRIMARY KEY,
    vendor_profile_id        BIGINT NOT NULL REFERENCES vendor_profiles(id) ON DELETE CASCADE,
    refresh_token_encrypted  TEXT NOT NULL,
    google_calendar_id       VARCHAR(255) NOT NULL,
    connected_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_google_calendar_connections_vendor_profile UNIQUE (vendor_profile_id)
);

ALTER TABLE bookings ADD COLUMN google_calendar_event_id VARCHAR(255);
