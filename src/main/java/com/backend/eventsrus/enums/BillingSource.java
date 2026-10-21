package com.backend.eventsrus.enums;

public enum BillingSource {
    FREE_GRANT,
    PAYPAL,
    // Paid manually via GCash - no payment API, so activation is a manual
    // admin review (see VendorSubscriptionService#submitGcashPayment/
    // verifyGcashPayment, AdminSubscriptionPaymentController).
    GCASH,
    // Bought in the Android app through Google Play Billing, relayed by
    // RevenueCat's webhook (see RevenueCatWebhookService). Cancelled and
    // managed in Google Play, never through our own screens.
    GOOGLE_PLAY
}
