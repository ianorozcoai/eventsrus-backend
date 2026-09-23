package com.backend.eventsrus.enums;

public enum BillingSource {
    FREE_GRANT,
    PAYPAL,
    // Paid manually via GCash - no payment API, so activation is a manual
    // admin review (see VendorSubscriptionService#submitGcashPayment/
    // verifyGcashPayment, AdminSubscriptionPaymentController).
    GCASH
}
