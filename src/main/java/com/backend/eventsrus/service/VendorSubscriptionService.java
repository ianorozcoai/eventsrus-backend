package com.backend.eventsrus.service;

import com.backend.eventsrus.config.PayPalProperties;
import com.backend.eventsrus.dto.AdminSubscriptionPaymentResponse;
import com.backend.eventsrus.dto.CreateSubscriptionRequest;
import com.backend.eventsrus.dto.CreateSubscriptionResponse;
import com.backend.eventsrus.dto.GcashHistoryEntryResponse;
import com.backend.eventsrus.dto.SubscriptionStatusResponse;
import com.backend.eventsrus.enums.BillingCycle;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.InvalidFileTypeException;
import com.backend.eventsrus.exception.InvalidRejectionReasonException;
import com.backend.eventsrus.exception.SubscriptionConflictException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.model.VendorSubscriptionEvent;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorSubscriptionEventRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class VendorSubscriptionService {

    // Quarterly has no discount (3 months at the flat rate); "1 month free"
    // on Semi-Annual, "2 months free" on Annual - see
    // SystemSettingKey#VENDOR_PRO_MONTHLY_PRICE.
    private static final int QUARTERLY_MONTHS_CHARGED = 3;
    private static final int SEMI_ANNUAL_MONTHS_CHARGED = 5;
    private static final int ANNUAL_MONTHS_CHARGED = 10;

    // The real calendar length of each cycle (as opposed to *_MONTHS_CHARGED
    // above, which is the discounted price multiplier) - used only for
    // GCash's manually-computed period, since there's no PayPal
    // next_billing_time to read for that path. Day-based to match every
    // other duration already in this codebase (VENDOR_TRIAL_DAYS, the
    // warning/grace windows) rather than calendar-month arithmetic.
    private static final int QUARTERLY_DAYS = 90;
    private static final int SEMI_ANNUAL_DAYS = 182;
    private static final int ANNUAL_DAYS = 365;

    // Matches UserService's own PRESIGNED_URL_TTL for the same purpose (a
    // private-document link sitting on an admin review page).
    private static final Duration PAYMENT_SCREENSHOT_URL_TTL = Duration.ofHours(1);

    private static final EnumSet<SubscriptionStatus> NON_TERMINAL_STATUSES = EnumSet.of(
            SubscriptionStatus.APPROVAL_PENDING,
            SubscriptionStatus.APPROVED,
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.SUSPENDED);

    private final UserRepository userRepository;
    private final VendorSubscriptionRepository vendorSubscriptionRepository;
    private final VendorSubscriptionEventRepository vendorSubscriptionEventRepository;
    private final VendorPlanService vendorPlanService;
    private final PayPalProperties payPalProperties;
    private final PayPalSubscriptionClient payPalSubscriptionClient;
    private final SystemSettingService systemSettingService;
    private final VendorBillingHistoryService vendorBillingHistoryService;
    private final S3UploadService s3UploadService;
    private final VendorReferralService vendorReferralService;
    private final VendorProfileRepository vendorProfileRepository;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public CreateSubscriptionResponse createSubscription(String email, CreateSubscriptionRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        // Only an existing PAYPAL-sourced subscription blocks a new one here
        // (double-subscribing) - a free/promo grant or a skipped trial must
        // NOT block this, since "Pay Now" from the required plan-selection
        // paywall (see PaywallModelAttributes) is exactly how a vendor on
        // one of those is meant to upgrade. Checking the stored status
        // column alone isn't enough either way: a lapsed grant's status
        // stays ACTIVE forever in the database (expiry is only ever
        // computed on read, via VendorPlanService - see its class Javadoc)
        // - so without the effective-plan check below, a vendor whose trial
        // expired would be permanently blocked from ever starting a real
        // paid subscription.
        boolean hasLivePaypalSubscription =
                vendorSubscriptionRepository.existsByUserIdAndBillingSourceAndStatusIn(
                        user.getId(), BillingSource.PAYPAL, NON_TERMINAL_STATUSES)
                        && !vendorPlanService.getEffectivePlan(user.getId()).expired();
        if (hasLivePaypalSubscription) {
            throw new SubscriptionConflictException("You already have an active or pending subscription.");
        }

        VendorSubscription subscription = vendorSubscriptionRepository.save(
                VendorSubscription.builder()
                        .user(user)
                        .plan(request.getPlan())
                        .billingCycle(request.getBillingCycle())
                        .billingSource(BillingSource.PAYPAL)
                        .status(SubscriptionStatus.APPROVAL_PENDING)
                        .build());

        String paypalPlanId = resolvePaypalPlanId(request.getPlan(), request.getBillingCycle());
        String returnUrl = frontendBaseUrl + "/subscription/return?vendorSubscriptionId=" + subscription.getId();
        String cancelUrl = frontendBaseUrl + "/subscription/plans";

        PayPalSubscriptionClient.CreatedSubscription created = payPalSubscriptionClient.createSubscription(
                paypalPlanId, String.valueOf(user.getId()), returnUrl, cancelUrl);

        subscription.setPaypalSubscriptionId(created.paypalSubscriptionId());
        subscription.setPaypalPlanId(paypalPlanId);
        vendorSubscriptionRepository.save(subscription);

        return CreateSubscriptionResponse.builder()
                .vendorSubscriptionId(subscription.getId())
                .approvalUrl(created.approveUrl())
                .build();
    }

    @Transactional
    public SubscriptionStatusResponse confirmSubscription(String email, Long vendorSubscriptionId) {
        VendorSubscription subscription = vendorSubscriptionRepository.findById(vendorSubscriptionId)
                .orElseThrow(() -> new IllegalStateException("Subscription not found: " + vendorSubscriptionId));

        if (!subscription.getUser().getEmail().equals(email)) {
            throw new IllegalStateException("Subscription does not belong to the authenticated user");
        }

        PayPalSubscriptionClient.PayPalSubscriptionDetails details =
                payPalSubscriptionClient.getSubscription(subscription.getPaypalSubscriptionId());

        applyPaypalStatus(subscription, details);
        vendorSubscriptionRepository.save(subscription);

        vendorSubscriptionEventRepository.save(
                VendorSubscriptionEvent.builder()
                        .vendorSubscription(subscription)
                        .paypalEventId("confirm-" + subscription.getId() + "-" + Instant.now().toEpochMilli())
                        .eventType("CLIENT_CONFIRM")
                        .payload(details.status())
                        .occurredAt(Instant.now())
                        .build());

        return statusResponseFor(subscription.getUser().getId());
    }

    /**
     * Records a subscription PayPal's JS SDK Smart Buttons already created
     * client-side (see fragments/common.html :: proPlanPickerForm's
     * onApprove) - unlike confirmSubscription, the VendorSubscription row
     * doesn't necessarily exist yet, since our backend never called PayPal
     * to create it this time. Find-or-create by paypalSubscriptionId (the
     * browser may retry onApprove) rather than requiring a pre-existing
     * row. Deliberately skips createSubscription's "already have a live
     * subscription" conflict check - PayPal has already accepted this
     * subscription on their end by the time this runs, so rejecting it here
     * would just orphan a real PayPal subscription with no local record.
     * Must complete before any PayPal webhook for this subscription arrives
     * - PayPalWebhookService#resolveSubscription only ever looks an existing
     * row up by paypalSubscriptionId, it never creates one.
     */
    @Transactional
    public SubscriptionStatusResponse recordApprovedSubscription(String email, String paypalSubscriptionId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        VendorSubscription subscription = vendorSubscriptionRepository.findByPaypalSubscriptionId(paypalSubscriptionId)
                .orElseGet(() -> VendorSubscription.builder()
                        .user(user)
                        .plan(PlanTier.PRO)
                        .billingSource(BillingSource.PAYPAL)
                        .status(SubscriptionStatus.APPROVAL_PENDING)
                        .paypalSubscriptionId(paypalSubscriptionId)
                        .build());

        if (!subscription.getUser().getEmail().equals(email)) {
            throw new IllegalStateException("Subscription does not belong to the authenticated user");
        }

        PayPalSubscriptionClient.PayPalSubscriptionDetails details =
                payPalSubscriptionClient.getSubscription(paypalSubscriptionId);

        applyPaypalStatus(subscription, details);
        vendorSubscriptionRepository.save(subscription);

        vendorSubscriptionEventRepository.save(
                VendorSubscriptionEvent.builder()
                        .vendorSubscription(subscription)
                        .paypalEventId("client-approve-" + paypalSubscriptionId + "-" + Instant.now().toEpochMilli())
                        .eventType("CLIENT_CONFIRM")
                        .payload(details.status())
                        .occurredAt(Instant.now())
                        .build());

        return statusResponseFor(user.getId());
    }

    public SubscriptionStatusResponse getStatus(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
        return statusResponseFor(user.getId());
    }

    private SubscriptionStatusResponse statusResponseFor(Long userId) {
        VendorPlanService.EffectivePlan effectivePlan = vendorPlanService.getEffectivePlan(userId);
        int monthlyPrice = systemSettingService.getInt(SystemSettingKey.VENDOR_PRO_MONTHLY_PRICE);
        PayPalProperties.PlanId planIds = payPalProperties.getPlanId();
        Optional<VendorSubscription> gcashRow = findGcashSubscription(userId);
        boolean gcashAwaitingVerification = gcashRow.map(sub -> sub.getStatus() == SubscriptionStatus.PAYMENT_VERIFICATION).orElse(false);
        boolean gcashRejected = gcashRow.map(sub -> sub.getStatus() == SubscriptionStatus.PAYMENT_REJECTED).orElse(false);
        return SubscriptionStatusResponse.builder()
                .plan(effectivePlan.plan())
                .expiresAt(effectivePlan.expiresAt())
                .expiringSoon(effectivePlan.expiringSoon())
                .expired(effectivePlan.expired())
                .inGracePeriod(effectivePlan.inGracePeriod())
                .graceEndsAt(effectivePlan.graceEndsAt())
                .monthlyPrice(monthlyPrice)
                .quarterlyPrice(monthlyPrice * QUARTERLY_MONTHS_CHARGED)
                .semiAnnualPrice(monthlyPrice * SEMI_ANNUAL_MONTHS_CHARGED)
                .annualPrice(monthlyPrice * ANNUAL_MONTHS_CHARGED)
                .billingSource(effectivePlan.billingSource())
                .monthlyPlanId(planIds.getProMonthly())
                .quarterlyPlanId(planIds.getProQuarterly())
                .semiAnnualPlanId(planIds.getProSemiAnnual())
                .annualPlanId(planIds.getProAnnual())
                .gcashAwaitingVerification(gcashAwaitingVerification)
                .gcashRejected(gcashRejected)
                .gcashRejectionReason(gcashRejected ? gcashRow.get().getRejectionReason() : null)
                .showWelcomePopup(computeShowWelcomePopup(userId, effectivePlan, gcashAwaitingVerification))
                .build();
    }

    /**
     * The vendor's single reused GCash-sourced row, if any - the one
     * submitGcashPayment/verifyGcashPayment/rejectGcashPayment all find and
     * update in place (see idx_vendor_subscriptions_one_gcash_row_per_user,
     * V49). Scoped to billingSource=GCASH and ordered by createdAt rather
     * than the cross-source, period-start-ordered
     * findFirstByUserIdOrderByCurrentPeriodStartDesc VendorPlanService uses:
     * a rejected row has currentPeriodStart=null, and Postgres' NULLS FIRST
     * on DESC would let an older, unrelated row with a real date wrongly
     * outrank it there (the same class of bug V49 fixed for submission).
     */
    private Optional<VendorSubscription> findGcashSubscription(Long userId) {
        return vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(userId, BillingSource.GCASH);
    }

    /**
     * True exactly once per vendor, the moment their plan is genuinely
     * active - not during a GCash submission's temporary 7-day grant, which
     * also has a non-null plan() but is still status=PAYMENT_VERIFICATION
     * on its underlying row.
     */
    private boolean computeShowWelcomePopup(
            Long userId, VendorPlanService.EffectivePlan effectivePlan, boolean gcashAwaitingVerification) {
        if (effectivePlan.plan() == null || gcashAwaitingVerification) {
            return false;
        }
        User user = userRepository.findById(userId).orElse(null);
        return user != null && !user.isProWelcomeShown();
    }

    /** "Got it" on the one-time Welcome to PRO popup - see fragments/common.html :: proWelcomeModal (web). */
    @Transactional
    public void markProWelcomeShown(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
        user.setProWelcomeShown(true);
        userRepository.save(user);
    }

    // How long a GCash submission's temporary access lasts before it needs
    // an admin to actually verify it - not admin-configurable like
    // VENDOR_TRIAL_DAYS, a short fixed window meant to keep the vendor
    // working while under review, not a real trial length.
    private static final int GCASH_PENDING_DAYS = 7;

    /**
     * "Upload Payment Screenshot" on the GCash popup (see
     * fragments/common.html :: gcashPaymentModal) - no payment API for
     * GCash, so this is purely a manual claim, but grants a real 7-day
     * period immediately (VendorPlanService#computeFrom already treats any
     * row with real period dates as a live plan, so the vendor gets full
     * working access right away with zero special-casing there) rather
     * than leaving the vendor locked out until an admin gets to it.
     * verifyGcashPayment below extends this to the real paid period; a
     * rejection revokes it. Only blocked by an existing submission still
     * mid-review - a rejected one is reused in place for the resubmission.
     */
    @Transactional
    public SubscriptionStatusResponse submitGcashPayment(
            String email, BillingCycle billingCycle, MultipartFile screenshot, String vendorRemarks) {
        if (screenshot == null || screenshot.isEmpty()) {
            throw new InvalidFileTypeException("A payment screenshot is required");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        Optional<VendorSubscription> existingGcash = findGcashSubscription(user.getId());
        if (existingGcash.isPresent() && existingGcash.get().getStatus() == SubscriptionStatus.PAYMENT_VERIFICATION) {
            throw new SubscriptionConflictException("Your previous GCash payment is still awaiting review.");
        }
        // A rejected (or nonexistent) GCash row leaves plan() null at this
        // point, so this only trips on a genuinely different live plan
        // (PayPal, promo, or an already-verified GCash subscription).
        if (vendorPlanService.getEffectivePlan(user.getId()).plan() != null) {
            throw new SubscriptionConflictException("You already have an active or pending subscription.");
        }

        String key = s3UploadService
                .upload(screenshot, "vendors/" + user.getId() + "/subscription-payment-screenshot", S3UploadService.Visibility.PRIVATE)
                .key();

        Instant now = Instant.now();
        Instant periodEnd = now.plus(GCASH_PENDING_DAYS, ChronoUnit.DAYS);
        VendorSubscription subscription = existingGcash.orElseGet(() -> VendorSubscription.builder().user(user).build());
        subscription.setPlan(PlanTier.PRO);
        subscription.setBillingCycle(billingCycle);
        subscription.setBillingSource(BillingSource.GCASH);
        subscription.setStatus(SubscriptionStatus.PAYMENT_VERIFICATION);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setPaymentScreenshotKey(key);
        subscription.setPaymentScreenshotUploadedAt(now);
        // A fresh submission supersedes any prior rejection - the reason no
        // longer applies to what's now under review.
        subscription.setRejectionReason(null);
        subscription.setVendorRemarks(vendorRemarks != null && !vendorRemarks.isBlank() ? vendorRemarks : null);
        try {
            vendorSubscriptionRepository.save(subscription);
        } catch (DataIntegrityViolationException e) {
            // Two near-simultaneous submissions (e.g. a double-click) can
            // both pass the existingGcash check above before either
            // commits - idx_vendor_subscriptions_one_gcash_row_per_user
            // (see V49) is the real backstop, this just turns the resulting
            // constraint violation into the same friendly error the
            // check above already gives a slower-arriving second request.
            throw new SubscriptionConflictException("Your previous GCash payment is still awaiting review.");
        }
        vendorBillingHistoryService.recordFreeGrant(subscription, now, periodEnd, BillingSource.GCASH);
        recordGcashEvent(subscription, "GCASH_SUBMITTED", subscription.getVendorRemarks());

        return statusResponseFor(user.getId());
    }

    /**
     * Admin approves a GCash submission (see AdminSubscriptionPaymentController)
     * - the real calendar-day length of billingCycle (see the *_DAYS
     * constants) since there's no PayPal next_billing_time to read for a
     * manual payment. Overwrites the temporary 7-day period submitGcashPayment
     * already granted. Mirrors PayPalWebhookService's PAYMENT.SALE.COMPLETED
     * handling: records the payment and converts a pending referral exactly
     * the same way a real PayPal payment already does.
     */
    @Transactional
    public void verifyGcashPayment(Long vendorUserId) {
        VendorSubscription subscription = findGcashSubscription(vendorUserId)
                .filter(sub -> sub.getStatus() == SubscriptionStatus.PAYMENT_VERIFICATION)
                .orElseThrow(() -> new IllegalStateException("No pending GCash payment for user: " + vendorUserId));

        Instant now = Instant.now();
        Instant periodEnd = now.plus(daysFor(subscription.getBillingCycle()), ChronoUnit.DAYS);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setRejectionReason(null);
        vendorSubscriptionRepository.save(subscription);

        int monthlyPrice = systemSettingService.getInt(SystemSettingKey.VENDOR_PRO_MONTHLY_PRICE);
        vendorBillingHistoryService.removeFreeGrant(subscription);
        vendorBillingHistoryService.recordPayment(
                subscription, BigDecimal.valueOf(priceFor(subscription.getBillingCycle(), monthlyPrice)),
                "PHP", null, now, periodEnd, now, BillingSource.GCASH);
        recordGcashEvent(subscription, "GCASH_VERIFIED", null);

        vendorReferralService.convertIfPending(vendorUserId);
    }

    /**
     * Admin rejects a GCash submission - revokes the temporary access
     * (clears the period dates back to null, so plan() goes back to null
     * and the vendor's next login shows the required popup again) but
     * keeps the row itself (and its screenshot) as an audit trail and as
     * the target a resubmission reuses, rather than deleting it. reason is
     * required - it's the whole point (see gcashRejectionReason on
     * SubscriptionStatusResponse and the vendor-facing "payment
     * verification failed" state it drives).
     */
    @Transactional
    public void rejectGcashPayment(Long vendorUserId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRejectionReasonException("A rejection reason is required");
        }
        VendorSubscription subscription = findGcashSubscription(vendorUserId)
                .filter(sub -> sub.getStatus() == SubscriptionStatus.PAYMENT_VERIFICATION)
                .orElseThrow(() -> new IllegalStateException("No pending GCash payment for user: " + vendorUserId));
        subscription.setStatus(SubscriptionStatus.PAYMENT_REJECTED);
        subscription.setCurrentPeriodStart(null);
        subscription.setCurrentPeriodEnd(null);
        subscription.setRejectionReason(reason);
        vendorSubscriptionRepository.save(subscription);
        recordGcashEvent(subscription, "GCASH_REJECTED", reason);
    }

    /** The admin module's GCash queue (all three tabs - Pending/Verified/Rejected) - see AdminSubscriptionPaymentController. */
    @Transactional(readOnly = true)
    public List<AdminSubscriptionPaymentResponse> listGcashPayments() {
        return vendorSubscriptionRepository.findByBillingSourceOrderByCreatedAtDesc(BillingSource.GCASH).stream()
                .map(sub -> {
                    VendorProfile profile = vendorProfileRepository.findByUserId(sub.getUser().getId()).orElse(null);
                    return AdminSubscriptionPaymentResponse.builder()
                            .vendorUserId(sub.getUser().getId())
                            .businessName(profile != null ? profile.getBusinessName() : null)
                            .ownerName(profile != null ? profile.getOwnerName() : null)
                            .billingCycle(sub.getBillingCycle())
                            .status(sub.getStatus())
                            .screenshotUrl(s3UploadService.presignedUrl(sub.getPaymentScreenshotKey(), PAYMENT_SCREENSHOT_URL_TTL))
                            .submittedAt(sub.getPaymentScreenshotUploadedAt())
                            .rejectionReason(sub.getRejectionReason())
                            .vendorRemarks(sub.getVendorRemarks())
                            .history(gcashHistoryFor(sub))
                            .build();
                })
                .toList();
    }

    private List<GcashHistoryEntryResponse> gcashHistoryFor(VendorSubscription subscription) {
        return vendorSubscriptionEventRepository.findByVendorSubscription_IdOrderByOccurredAtDesc(subscription.getId()).stream()
                .map(event -> GcashHistoryEntryResponse.builder()
                        .eventType(event.getEventType())
                        .note(event.getPayload())
                        .occurredAt(event.getOccurredAt())
                        .build())
                .toList();
    }

    /** One row in a vendor's GCash review timeline - see AdminSubscriptionPaymentResponse#history. */
    private void recordGcashEvent(VendorSubscription subscription, String eventType, String note) {
        vendorSubscriptionEventRepository.save(
                VendorSubscriptionEvent.builder()
                        .vendorSubscription(subscription)
                        .paypalEventId(eventType.toLowerCase(java.util.Locale.ROOT).replace('_', '-')
                                + "-" + subscription.getId() + "-" + Instant.now().toEpochMilli())
                        .eventType(eventType)
                        .payload(note)
                        .occurredAt(Instant.now())
                        .build());
    }

    private int daysFor(BillingCycle cycle) {
        return switch (cycle) {
            case MONTHLY -> 30;
            case QUARTERLY -> QUARTERLY_DAYS;
            case SEMI_ANNUAL -> SEMI_ANNUAL_DAYS;
            case ANNUAL -> ANNUAL_DAYS;
        };
    }

    private int priceFor(BillingCycle cycle, int monthlyPrice) {
        return switch (cycle) {
            case MONTHLY -> monthlyPrice;
            case QUARTERLY -> monthlyPrice * QUARTERLY_MONTHS_CHARGED;
            case SEMI_ANNUAL -> monthlyPrice * SEMI_ANNUAL_MONTHS_CHARGED;
            case ANNUAL -> monthlyPrice * ANNUAL_MONTHS_CHARGED;
        };
    }

    /**
     * Pushes the admin-configured monthly price (and its derived Semi-Annual
     * /Annual totals) onto the 3 already-created PayPal Plans so what's
     * actually charged matches what SystemSettingKey#VENDOR_PRO_MONTHLY_PRICE
     * says - called from AdminSystemSettingController right before that
     * setting is saved, so the two never drift apart. Updates whichever
     * PayPal environment this running instance is configured for (sandbox
     * locally, live in production) - see PayPalProperties#getBaseUrl.
     */
    public void syncProPricingToPayPal(int monthlyPrice) {
        PayPalProperties.PlanId planIds = payPalProperties.getPlanId();
        payPalSubscriptionClient.updatePlanPricing(planIds.getProMonthly(), monthlyPrice + ".00");
        payPalSubscriptionClient.updatePlanPricing(
                planIds.getProQuarterly(), (monthlyPrice * QUARTERLY_MONTHS_CHARGED) + ".00");
        payPalSubscriptionClient.updatePlanPricing(
                planIds.getProSemiAnnual(), (monthlyPrice * SEMI_ANNUAL_MONTHS_CHARGED) + ".00");
        payPalSubscriptionClient.updatePlanPricing(planIds.getProAnnual(), (monthlyPrice * ANNUAL_MONTHS_CHARGED) + ".00");
    }

    private void applyPaypalStatus(
            VendorSubscription subscription, PayPalSubscriptionClient.PayPalSubscriptionDetails details) {
        SubscriptionStatus newStatus = SubscriptionStatus.valueOf(details.status());
        boolean justActivated = subscription.getStatus() != SubscriptionStatus.ACTIVE
                && newStatus == SubscriptionStatus.ACTIVE;

        subscription.setStatus(newStatus);
        if (justActivated) {
            subscription.setCurrentPeriodStart(Instant.now());
        }
        if (details.nextBillingTime() != null) {
            subscription.setCurrentPeriodEnd(details.nextBillingTime());
        }
    }

    // Only PRO exists as a plan for now, but this stays a switch (not a
    // direct field read) so re-adding a tier later is a one-line addition,
    // not a reshape.
    private String resolvePaypalPlanId(PlanTier plan, BillingCycle cycle) {
        PayPalProperties.PlanId planIds = payPalProperties.getPlanId();
        return switch (plan) {
            case PRO -> switch (cycle) {
                case MONTHLY -> planIds.getProMonthly();
                case QUARTERLY -> planIds.getProQuarterly();
                case SEMI_ANNUAL -> planIds.getProSemiAnnual();
                case ANNUAL -> planIds.getProAnnual();
            };
        };
    }
}
