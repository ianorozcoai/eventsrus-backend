package com.backend.eventsrus.enums;

public enum SubscriptionStatus {
    APPROVAL_PENDING,
    APPROVED,
    ACTIVE,
    SUSPENDED,
    CANCELLED,
    EXPIRED,
    // A GCash screenshot was uploaded but an admin hasn't reviewed it yet -
    // see VendorSubscriptionService#submitGcashPayment/verifyGcashPayment.
    PAYMENT_VERIFICATION,
    // An admin rejected a GCash submission (see
    // VendorSubscriptionService#rejectGcashPayment) - the row is kept
    // (audit trail, resubmission target) rather than deleted.
    PAYMENT_REJECTED
}
