package com.backend.eventsrus.enums;

public enum BookingStatus {
    PROPOSED,
    APPROVED,
    DECLINED,
    CANCELLED,

    // Planner-initiated path, born from a RESPONDED Quotation - see
    // BookingService#bookFromQuotation/submitPaymentScreenshot/
    // acknowledgePayment/rejectPayment.
    AWAITING_PAYMENT,
    PAYMENT_SUBMITTED,
    BOOKED,
    PAYMENT_REJECTED
}
