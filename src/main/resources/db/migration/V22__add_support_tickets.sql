-- Support ticketing for planners and vendors - a way to raise a concern
-- about a specific transaction or the system in general. Same metadata/
-- messages split as conversations (Conversation/ConversationMessage).
CREATE TABLE support_tickets (
    id                     BIGSERIAL PRIMARY KEY,
    raised_by_user_id      BIGINT NOT NULL REFERENCES users(id),
    subject                VARCHAR(255) NOT NULL,
    category               VARCHAR(30) NOT NULL,
    status                 VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    related_event_id       BIGINT,
    related_booking_id     BIGINT,
    related_quotation_id   BIGINT,
    assigned_admin_user_id BIGINT REFERENCES users(id),
    resolved_at            TIMESTAMPTZ,
    closed_at              TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_support_tickets_raised_by_user_id ON support_tickets(raised_by_user_id);

CREATE TABLE support_ticket_messages (
    id               BIGSERIAL PRIMARY KEY,
    ticket_id        BIGINT NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    sender_user_id   BIGINT NOT NULL REFERENCES users(id),
    body             TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_support_ticket_messages_ticket_id ON support_ticket_messages(ticket_id);
