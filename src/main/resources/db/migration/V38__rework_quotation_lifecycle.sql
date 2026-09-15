-- Phase 1 of the quotation/booking lifecycle rework (see project memory
-- quotation-booking-target-state-machine): promotes REQUESTED/RESPONDED to
-- their spec names and adds the negotiation-version + accept/deposit/
-- payment-review states that didn't exist at all on Quotation before. Old
-- Booking rows/flow (AWAITING_PAYMENT/PAYMENT_SUBMITTED/PAYMENT_REJECTED)
-- are untouched - new quotation-originated bookings are now created
-- directly at BOOKED (see QuotationService#acceptBooking), so that old
-- sub-flow only remains live for whatever already existed before this
-- migration.

UPDATE quotations SET status = 'REQUEST_FOR_QUOTE' WHERE status = 'REQUESTED';
UPDATE quotations SET status = 'QUOTE_SENT' WHERE status = 'RESPONDED';
UPDATE quotation_status_history SET from_status = 'REQUEST_FOR_QUOTE' WHERE from_status = 'REQUESTED';
UPDATE quotation_status_history SET from_status = 'QUOTE_SENT' WHERE from_status = 'RESPONDED';
UPDATE quotation_status_history SET to_status = 'REQUEST_FOR_QUOTE' WHERE to_status = 'REQUESTED';
UPDATE quotation_status_history SET to_status = 'QUOTE_SENT' WHERE to_status = 'RESPONDED';

ALTER TABLE quotations
    ADD COLUMN version INT NOT NULL DEFAULT 1,
    ADD COLUMN quoted_amount NUMERIC(12,2),
    ADD COLUMN accepted_at TIMESTAMPTZ,
    ADD COLUMN payment_screenshot_key VARCHAR(500),
    ADD COLUMN payment_screenshot_uploaded_at TIMESTAMPTZ,
    ADD COLUMN payment_rejection_reason TEXT;

ALTER TABLE quotation_status_history
    ADD COLUMN version INT NOT NULL DEFAULT 1,
    ADD COLUMN quoted_amount NUMERIC(12,2),
    ADD COLUMN target_date DATE;

-- Snapshots which packages were being discussed at each transition - unlike
-- quotations' own package list (quotation_packages), which only ever holds
-- the CURRENT ask and gets overwritten on every revision (see
-- QuotationService#requestRevision), this is append-only, one row per
-- history entry.
CREATE TABLE quotation_status_history_packages (
    history_id        BIGINT NOT NULL REFERENCES quotation_status_history(id) ON DELETE CASCADE,
    vendor_package_id BIGINT NOT NULL REFERENCES vendor_packages(id) ON DELETE CASCADE,
    PRIMARY KEY (history_id, vendor_package_id)
);

-- Only ever set on a Booking created through the new QuotationService#
-- acceptBooking path - null for every booking that predates this migration
-- and for the vendor cold-proposal flow (BookingService#propose).
ALTER TABLE bookings
    ADD COLUMN payment_type VARCHAR(10),
    ADD COLUMN confirmation_message TEXT;
