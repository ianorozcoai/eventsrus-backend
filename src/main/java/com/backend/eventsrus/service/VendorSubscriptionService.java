package com.backend.eventsrus.service;

import com.backend.eventsrus.config.PayPalProperties;
import com.backend.eventsrus.dto.CreateSubscriptionRequest;
import com.backend.eventsrus.dto.CreateSubscriptionResponse;
import com.backend.eventsrus.dto.SubscriptionStatusResponse;
import com.backend.eventsrus.enums.BillingCycle;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.SubscriptionConflictException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.model.VendorSubscriptionEvent;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorSubscriptionEventRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.time.Instant;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VendorSubscriptionService {

    // Quarterly has no discount (3 months at the flat rate); "1 month free"
    // on Semi-Annual, "2 months free" on Annual - see
    // SystemSettingKey#VENDOR_PRO_MONTHLY_PRICE.
    private static final int QUARTERLY_MONTHS_CHARGED = 3;
    private static final int SEMI_ANNUAL_MONTHS_CHARGED = 5;
    private static final int ANNUAL_MONTHS_CHARGED = 10;

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

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public CreateSubscriptionResponse createSubscription(String email, CreateSubscriptionRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        // Checking the stored status column alone isn't enough: a lapsed
        // free-trial grant's status stays ACTIVE forever in the database
        // (expiry is only ever computed on read, via VendorPlanService -
        // see its class Javadoc) - so without the effective-plan check
        // below, a vendor whose trial expired would be permanently blocked
        // from ever starting a real paid subscription.
        boolean hasLiveNonTerminalSubscription =
                vendorSubscriptionRepository.existsByUserIdAndStatusIn(user.getId(), NON_TERMINAL_STATUSES)
                        && !vendorPlanService.getEffectivePlan(user.getId()).expired();
        if (hasLiveNonTerminalSubscription) {
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

    public SubscriptionStatusResponse getStatus(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
        return statusResponseFor(user.getId());
    }

    private SubscriptionStatusResponse statusResponseFor(Long userId) {
        VendorPlanService.EffectivePlan effectivePlan = vendorPlanService.getEffectivePlan(userId);
        int monthlyPrice = systemSettingService.getInt(SystemSettingKey.VENDOR_PRO_MONTHLY_PRICE);
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
                .build();
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
