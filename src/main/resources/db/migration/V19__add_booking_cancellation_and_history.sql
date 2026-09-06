-- Real cancellation support - BookingStatus.CANCELLED existed with no code
-- path that ever set it. Either participant can now cancel from any
-- non-terminal status with a mandatory reason (see BookingService#cancel).
ALTER TABLE bookings ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN cancellation_reason TEXT;
ALTER TABLE bookings ADD COLUMN cancelled_by_user_id BIGINT REFERENCES users(id);

-- Append-only audit trail of every status change a booking goes through -
-- fixes Booking#paymentRejectionReason (and every other single-value status
-- field) only ever holding the latest value, losing the full back-and-forth.
CREATE TABLE booking_status_history (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by_user_id BIGINT NOT NULL REFERENCES users(id),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_booking_status_history_booking_id ON booking_status_history(booking_id);
