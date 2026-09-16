-- Lets the quotation history timeline show the payment screenshot that was
-- under review at a PAYMENT_REVIEW transition, and the invoice/receipt the
-- vendor attached at the BOOKED transition - both currently only live on
-- the live Quotation/Booking rows, which get overwritten on the next
-- screenshot resubmission, losing what was actually shown at that point.
ALTER TABLE quotation_status_history
    ADD COLUMN payment_screenshot_key VARCHAR(500),
    ADD COLUMN invoice_key VARCHAR(500);
