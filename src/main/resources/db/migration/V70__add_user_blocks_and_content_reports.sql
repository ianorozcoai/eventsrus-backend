-- Report and block (Google Play User Generated Content policy). A block stops two users
-- messaging each other; a report is just a note to the admin and changes nothing by itself.
CREATE TABLE user_blocks (
    id              BIGSERIAL PRIMARY KEY,
    blocker_user_id BIGINT NOT NULL REFERENCES users(id),
    blocked_user_id BIGINT NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_blocks UNIQUE (blocker_user_id, blocked_user_id)
);

CREATE TABLE content_reports (
    id               BIGSERIAL PRIMARY KEY,
    reporter_user_id BIGINT NOT NULL REFERENCES users(id),
    reported_user_id BIGINT NOT NULL REFERENCES users(id),
    content_type     VARCHAR(20) NOT NULL,
    content_id       BIGINT NOT NULL,
    reason           VARCHAR(30) NOT NULL,
    details          TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolved_at      TIMESTAMPTZ,
    resolved_note    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- one report per person per item, so nobody can spam the admin inbox
    CONSTRAINT uk_content_reports_once UNIQUE (reporter_user_id, content_type, content_id)
);

CREATE INDEX idx_content_reports_status ON content_reports (status, created_at DESC);
