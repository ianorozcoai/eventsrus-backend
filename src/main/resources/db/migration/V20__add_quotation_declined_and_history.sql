-- A planner can now formally close out a quote without booking it (see
-- QuotationService#declineQuotation), instead of it sitting as RESPONDED
-- forever on the vendor's dashboard.
ALTER TABLE quotations ADD COLUMN declined_at TIMESTAMPTZ;

-- Same append-only audit trail as booking_status_history - also captures
-- the full text of every revision request (QuotationService#requestRevision),
-- since Quotation#requestMessage itself only ever holds the latest ask.
CREATE TABLE quotation_status_history (
    id BIGSERIAL PRIMARY KEY,
    quotation_id BIGINT NOT NULL REFERENCES quotations(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by_user_id BIGINT NOT NULL REFERENCES users(id),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_quotation_status_history_quotation_id ON quotation_status_history(quotation_id);
