CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    planner_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(255),
    event_type VARCHAR(30) NOT NULL,
    event_date DATE,
    location VARCHAR(255),
    description TEXT,
    ai_idea_text TEXT,
    saved BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_events_planner_id ON events(planner_id);

CREATE TABLE event_suggested_vendors (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    vendor_profile_id BIGINT REFERENCES vendor_profiles(id),
    vendor_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_event_suggested_vendors_event_id ON event_suggested_vendors(event_id);

CREATE TABLE event_checklist_items (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    label VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    source VARCHAR(20) NOT NULL DEFAULT 'CUSTOM',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_event_checklist_items_event_id ON event_checklist_items(event_id);

CREATE TABLE leads (
    id BIGSERIAL PRIMARY KEY,
    vendor_user_id BIGINT NOT NULL REFERENCES users(id),
    planner_user_id BIGINT NOT NULL REFERENCES users(id),
    event_id BIGINT NOT NULL REFERENCES events(id),
    first_visited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_visited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_leads_vendor_planner_event UNIQUE (vendor_user_id, planner_user_id, event_id)
);
CREATE INDEX idx_leads_vendor_user_id ON leads(vendor_user_id);

CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    vendor_user_id BIGINT NOT NULL REFERENCES users(id),
    planner_user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversations_event_vendor UNIQUE (event_id, vendor_user_id)
);
CREATE INDEX idx_conversations_vendor_user_id ON conversations(vendor_user_id);
CREATE INDEX idx_conversations_planner_user_id ON conversations(planner_user_id);

CREATE TABLE conversation_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id),
    sender_user_id BIGINT NOT NULL REFERENCES users(id),
    target_date DATE,
    body TEXT NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversation_messages_conversation_id ON conversation_messages(conversation_id);

CREATE TABLE quotations (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    vendor_user_id BIGINT NOT NULL REFERENCES users(id),
    planner_user_id BIGINT NOT NULL REFERENCES users(id),
    target_date DATE,
    request_message TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    pdf_key VARCHAR(500),
    responded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_quotations_vendor_user_id ON quotations(vendor_user_id);
CREATE INDEX idx_quotations_event_id ON quotations(event_id);

CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    vendor_user_id BIGINT NOT NULL REFERENCES users(id),
    planner_user_id BIGINT NOT NULL REFERENCES users(id),
    price NUMERIC(12,2),
    event_datetime TIMESTAMPTZ,
    agreement_details TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED',
    proposed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_bookings_vendor_user_id ON bookings(vendor_user_id);
CREATE INDEX idx_bookings_planner_user_id ON bookings(planner_user_id);
CREATE INDEX idx_bookings_event_id ON bookings(event_id);

CREATE TABLE vendor_packages (
    id BIGSERIAL PRIMARY KEY,
    vendor_profile_id BIGINT NOT NULL REFERENCES vendor_profiles(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(12,2),
    package_type VARCHAR(20) NOT NULL DEFAULT 'SERVICE',
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vendor_packages_vendor_profile_id ON vendor_packages(vendor_profile_id);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT,
    related_entity_type VARCHAR(30),
    related_entity_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_recipient_user_id ON notifications(recipient_user_id);

-- Vendor account settings: business scope + rules/policies (screenshots tabs 2 & 3).
ALTER TABLE vendor_profiles ADD COLUMN primary_category VARCHAR(30);
ALTER TABLE vendor_profiles ADD COLUMN max_guest_capacity INTEGER;
ALTER TABLE vendor_profiles ADD COLUMN base_price NUMERIC(12,2);
ALTER TABLE vendor_profiles ADD COLUMN lead_time_days INTEGER;
ALTER TABLE vendor_profiles ADD COLUMN storefront_overview TEXT;
ALTER TABLE vendor_profiles ADD COLUMN retainer_percentage INTEGER;
ALTER TABLE vendor_profiles ADD COLUMN cancellation_policy VARCHAR(255);
ALTER TABLE vendor_profiles ADD COLUMN refund_terms TEXT;
