-- A vendor must attach an invoice/receipt document when acknowledging a
-- planner's payment screenshot - see BookingService#acknowledgePayment.
-- Same private-bucket-key + uploaded-at shape as payment_screenshot_key
-- above, just the vendor's side of the paper trail instead of the planner's.
ALTER TABLE bookings ADD COLUMN invoice_key VARCHAR(500);
ALTER TABLE bookings ADD COLUMN invoice_uploaded_at TIMESTAMPTZ;
