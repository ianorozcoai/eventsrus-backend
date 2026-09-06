package com.backend.eventsrus.service;

import com.backend.eventsrus.config.PayPalProperties;
import com.backend.eventsrus.dto.CreateSubscriptionRequest;
import com.backend.eventsrus.dto.CreateSubscriptionResponse;
import com.backend.eventsrus.dto.SubscriptionStatusResponse;
import com.backend.eventsrus.enums.BillingCycle;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.SubscriptionStatus;
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
        return SubscriptionStatusResponse.builder()
                .plan(effectivePlan.plan())
                .expiresAt(effectivePlan.expiresAt())
                .expiringSoon(effectivePlan.expiringSoon())
                .expired(effectivePlan.expired())
                .build();
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
            case PRO -> cycle == BillingCycle.MONTHLY ? planIds.getProMonthly() : planIds.getProAnnual();
        };
    }
}
