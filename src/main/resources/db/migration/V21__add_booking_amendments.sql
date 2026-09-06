-- Change orders against an already-BOOKED booking - one side proposes a new
-- price/date/details/packages, the other accepts or rejects, rather than
-- either silently overwriting the confirmed booking's fields with no trace
-- of what it used to say. See BookingAmendmentService.
CREATE TABLE booking_amendments (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    proposed_by_user_id BIGINT NOT NULL REFERENCES users(id),
    new_price NUMERIC(12,2),
    new_event_datetime TIMESTAMPTZ,
    new_agreement_details TEXT,
    note TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_booking_amendments_booking_id ON booking_amendments(booking_id);

CREATE TABLE booking_amendment_packages (
    booking_amendment_id BIGINT NOT NULL REFERENCES booking_amendments(id) ON DELETE CASCADE,
    vendor_package_id BIGINT NOT NULL REFERENCES vendor_packages(id) ON DELETE CASCADE,
    PRIMARY KEY (booking_amendment_id, vendor_package_id)
);
