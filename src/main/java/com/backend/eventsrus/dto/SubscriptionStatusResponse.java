package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.PlanTier;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SubscriptionStatusResponse {

    private PlanTier plan;
    private Instant expiresAt;
    private boolean expiringSoon;
    private boolean expired;
    private boolean inGracePeriod;
    private Instant graceEndsAt;
    private int monthlyPrice;
    private int quarterlyPrice;
    private int semiAnnualPrice;
    private int annualPrice;
    private BillingSource billingSource;
    // PayPal Plan IDs for the JS SDK Smart Buttons picker (fragments/common.html
    // :: proPlanPickerForm) - not secret, meaningless without our own PayPal
    // credentials, so bundling them with the already-public prices above
    // avoids a separate endpoint.
    private String monthlyPlanId;
    private String quarterlyPlanId;
    private String semiAnnualPlanId;
    private String annualPlanId;
    // True exactly once, the first time this vendor's plan is genuinely
    // active (promo trial or PayPal: immediately; GCash: only once an admin
    // actually verifies, never during the temporary 7-day grant) - see
    // VendorSubscriptionService#statusResponseFor/markProWelcomeShown.
    private boolean showWelcomePopup;
    // True only while a GCash submission's temporary 7-day grant is still
    // mid-review (plan() is already non-null at this point, since the grant
    // gives real working access immediately) - see
    // VendorSubscriptionService#isGcashAwaitingVerification. Lets the web
    // layer distinguish "free trial or genuinely paid PRO" from "PRO access
    // on a temporary grant, payment not yet confirmed" for display (e.g.
    // "Free-Pro" vs "Pro" on vendor/subscription.html).
    private boolean gcashAwaitingVerification;
    // True when the vendor's most recent GCash submission was rejected and
    // they haven't resubmitted yet - see VendorSubscriptionService#
    // rejectGcashPayment. gcashRejectionReason is the admin's stated reason,
    // null unless gcashRejected is true.
    private boolean gcashRejected;
    private String gcashRejectionReason;
}
