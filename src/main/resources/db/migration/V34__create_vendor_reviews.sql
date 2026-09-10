-- One planner review per completed booking. booking_id is UNIQUE so a
-- booking can only ever have one review; vendor_user_id is denormalised
-- (it never changes for a booking) so the storefront can pull a vendor's
-- reviews without joining through bookings every time.
CREATE TABLE vendor_reviews (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE REFERENCES bookings(id),
    vendor_user_id BIGINT NOT NULL REFERENCES users(id),
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT NOT NULL,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vendor_reviews_vendor_user_id ON vendor_reviews(vendor_user_id);
