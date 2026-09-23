package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BillingCycle;
import com.backend.eventsrus.enums.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** One row of the admin module's GCash payment-verification queue - see AdminSubscriptionPaymentController. */
@Getter
@Builder
@AllArgsConstructor
public class AdminSubscriptionPaymentResponse {

    private Long vendorUserId;
    private String businessName;
    private String ownerName;
    private BillingCycle billingCycle;
    private SubscriptionStatus status;
    private String screenshotUrl;
    private Instant submittedAt;
    // Both reflect only the current row's latest state - see
    // VendorSubscription#rejectionReason/vendorRemarks. rejectionReason is
    // set only while status==PAYMENT_REJECTED; vendorRemarks is the
    // vendor's own note from their most recent submission, if any.
    private String rejectionReason;
    private String vendorRemarks;
    // Every submit/verify/reject for this vendor's GCash row, newest first
    // - see VendorSubscriptionService#listGcashPayments.
    private List<GcashHistoryEntryResponse> history;
}
