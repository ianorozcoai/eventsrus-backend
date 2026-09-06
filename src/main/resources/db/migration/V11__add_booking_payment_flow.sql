ALTER TABLE bookings ADD COLUMN quotation_id BIGINT REFERENCES quotations(id);
ALTER TABLE bookings ADD COLUMN payment_screenshot_key VARCHAR(500);
ALTER TABLE bookings ADD COLUMN payment_screenshot_uploaded_at TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN payment_acknowledged_at TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN payment_rejection_reason TEXT;
CREATE INDEX idx_bookings_quotation_id ON bookings(quotation_id);
