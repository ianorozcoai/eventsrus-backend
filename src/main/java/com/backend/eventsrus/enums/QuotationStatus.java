package com.backend.eventsrus.enums;

/**
 * A Quotation's full negotiation-to-booking lifecycle (Phase 1 of the
 * project's quotation/booking lifecycle spec - see project memory
 * quotation-booking-target-state-machine for the full picture, including
 * the later Phase 2/3 statuses that don't exist yet).
 *
 * <p>REQUEST_FOR_QUOTE/QUOTE_SENT/REVISION_REQUESTED/REVISION_SENT are the
 * open negotiation loop - QuotationService#requestQuotation/#respondWithPdf/
 * #requestRevision move between these, incrementing Quotation#version each
 * time a new ask or a new response goes out. From QUOTE_ACCEPTED onward the
 * quote is frozen (the "Booking Conversion" rule) - no more revisions, no
 * decline, only the deposit/payment/booking pipeline below.
 */
public enum QuotationStatus {

    /** Planner's original ask from a vendor's storefront - see QuotationService#requestQuotation. */
    REQUEST_FOR_QUOTE,

    /** Vendor's quote (an initial one or a resend) is out, awaiting the planner - see QuotationService#respondWithPdf. */
    QUOTE_SENT,

    /** Planner asked for changes to the last sent quote - see QuotationService#requestRevision. */
    REVISION_REQUESTED,

    /** Vendor resent an updated quote after a revision request - see QuotationService#respondWithPdf. */
    REVISION_SENT,

    /**
     * Transient - the planner just accepted a QUOTE_SENT/REVISION_SENT
     * quote. Immediately auto-resolves to PENDING_DEPOSIT or PAYMENT_REVIEW
     * in the same call (see QuotationService#acceptQuote), so this is never
     * a resting value on Quotation#status - but it's always recorded as its
     * own quotation_status_history row so the acceptance moment stays
     * visible in the timeline.
     */
    QUOTE_ACCEPTED,

    /** Accepted, no payment screenshot yet - the planner can upload one whenever ready (QuotationService#submitPaymentScreenshot). */
    PENDING_DEPOSIT,

    /** Planner submitted a payment screenshot; the vendor needs to verify it. */
    PAYMENT_REVIEW,

    /** Vendor rejected the screenshot - planner must resubmit (back to PAYMENT_REVIEW via QuotationService#submitPaymentScreenshot). */
    PAYMENT_REJECTED,

    /**
     * Vendor verified payment and confirmed the booking - see
     * QuotationService#acceptBooking, which creates the actual Booking row
     * at exactly this point (not any earlier in this flow). Terminal; the
     * quote and its new booking are both locked from here - further
     * scope/price changes route through a BookingAmendment instead (the
     * Addendum Pattern rule, unaffected by this Phase 1 work).
     */
    BOOKED,

    /** Planner formally closed this out without booking - see QuotationService#declineQuotation. Only reachable pre-acceptance. */
    DECLINED,

    /**
     * The Booking this quotation converted into (see BOOKED above) was
     * later cancelled - see BookingService#cancel, which mirrors the
     * cancellation back onto the Quotation so the two never show
     * contradictory statuses (a vendor or planner seeing "CANCELLED" on the
     * booking but "BOOKED" on its quotation was confusing). Distinct from
     * DECLINED, which means it never got this far.
     */
    CANCELLED
}
