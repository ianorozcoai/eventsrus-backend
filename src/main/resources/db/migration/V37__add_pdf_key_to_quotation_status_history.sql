-- Each vendor response overwrote quotations.pdf_key with no record of what
-- was sent before it - the audit trail already captured the planner's side
-- of every revision (reason), now it captures the vendor's PDF too, so a
-- back-and-forth negotiation stays fully visible instead of the previous
-- version silently disappearing. See QuotationService#respondWithPdf.
ALTER TABLE quotation_status_history ADD COLUMN pdf_key VARCHAR(500);
