package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.SubscriptionExpiredException;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The single source of truth for "is this vendor's plan currently good" -
 * computed fresh on every call from VendorSubscription's stored dates,
 * never cached or written back. That's deliberate: nothing sweeps a lapsed
 * row's status to EXPIRED on a schedule, so any code that trusted the
 * stored status column alone would see a subscription that expired months
 * ago as still "ACTIVE". Everything that cares whether a vendor's plan is
 * live - the paywall, the gating on quotations/chat/bookings, vendor
 * search - should go through this class, not VendorSubscription directly.
 */
@Service
@RequiredArgsConstructor
public class VendorPlanService {

    private final VendorSubscriptionRepository vendorSubscriptionRepository;
    private final SystemSettingService systemSettingService;

    public EffectivePlan getEffectivePlan(Long userId) {
        return vendorSubscriptionRepository.findFirstByUserIdOrderByCurrentPeriodStartDesc(userId)
                .map(this::computeFrom)
                .orElseGet(() -> new EffectivePlan(null, null, false, false));
    }

    /** Throws if the vendor has no live plan - use to gate quotations, chat, and bookings on the vendor's side. */
    public void requireActiveSubscription(Long vendorUserId) {
        if (getEffectivePlan(vendorUserId).plan() == null) {
            throw new SubscriptionExpiredException(
                    "Your subscription has ended. Renew your plan to use this feature.");
        }
    }

    private EffectivePlan computeFrom(VendorSubscription subscription) {
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        if (periodEnd == null) {
            // Still APPROVAL_PENDING — PayPal hasn't confirmed a billing period yet.
            return new EffectivePlan(null, null, false, false);
        }

        Instant now = Instant.now();
        if (periodEnd.isBefore(now)) {
            return new EffectivePlan(null, periodEnd, false, true);
        }

        boolean expiringSoon = periodEnd.isBefore(
                now.plus(systemSettingService.getInt(SystemSettingKey.SUBSCRIPTION_WARNING_DAYS), ChronoUnit.DAYS));
        return new EffectivePlan(subscription.getPlan(), periodEnd, expiringSoon, false);
    }

    public record EffectivePlan(PlanTier plan, Instant expiresAt, boolean expiringSoon, boolean expired) {
    }
}
