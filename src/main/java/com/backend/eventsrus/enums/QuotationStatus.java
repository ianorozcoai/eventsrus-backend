package com.backend.eventsrus.enums;

public enum QuotationStatus {
    REQUESTED,
    RESPONDED,

    // Planner formally closed this out without booking - either they went
    // with a different vendor's quote or decided not to proceed at all. See
    // QuotationService#declineQuotation. A REQUESTED quotation can also be
    // sent back to REQUESTED from RESPONDED via #requestRevision - that's
    // not a new status, just a transition back to an existing one.
    DECLINED
}
