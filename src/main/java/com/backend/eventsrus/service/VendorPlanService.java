package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.SubscriptionExpiredException;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.BookingStatusEventRepository;
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
    private final BookingStatusEventRepository bookingStatusEventRepository;

    public EffectivePlan getEffectivePlan(Long userId) {
        return vendorSubscriptionRepository.findFirstByUserIdOrderByCurrentPeriodStartDesc(userId)
                .map(this::computeFrom)
                .orElseGet(() -> new EffectivePlan(null, null, false, false, false, null, null));
    }

    /** Throws if the vendor has no live plan - use to gate quotations, chat, and bookings on the vendor's side. */
    public void requireActiveSubscription(Long vendorUserId) {
        if (getEffectivePlan(vendorUserId).plan() == null) {
            throw new SubscriptionExpiredException(
                    "Your subscription has ended. Renew your plan to use this feature.");
        }
    }

    /**
     * Whole days (rounded up) left before the current plan ends, 0 if there is none or it has already
     * ended (grace period not counted). A new paid plan stores this as its carry-over, so paying early
     * adds the new plan's days on top of what was left instead of replacing them.
     */
    public int remainingDays(Long userId) {
        // A free-until-bookings grant is not a number of days: paying early forfeits what is left of it
        // (otherwise up to a year of free days would stack on top of the paid plan).
        if (vendorSubscriptionRepository.findFirstByUserIdOrderByCurrentPeriodStartDesc(userId)
                .map(sub -> sub.getBookingsGoal() != null).orElse(false)) {
            return 0;
        }
        EffectivePlan plan = getEffectivePlan(userId);
        if (plan.plan() == null || plan.expiresAt() == null || !plan.expiresAt().isAfter(Instant.now())) {
            return 0;
        }
        long seconds = java.time.Duration.between(Instant.now(), plan.expiresAt()).getSeconds();
        return (int) ((seconds + 86_399L) / 86_400L);
    }

    private EffectivePlan computeFrom(VendorSubscription subscription) {
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        if (periodEnd == null) {
            // Still APPROVAL_PENDING — PayPal hasn't confirmed a billing period yet.
            return new EffectivePlan(null, null, false, false, false, null, subscription.getBillingSource());
        }
        // Free until N counted bookings: the free period ends at the moment the Nth booking was counted,
        // or at the deadline already stored as the period end, whichever is earlier. After that the
        // normal grace period and paywall apply, exactly as for a date-based plan.
        if (subscription.getBookingsGoal() != null && subscription.getBillingSource() == BillingSource.FREE_GRANT) {
            Instant goalReachedAt = goalReachedAt(subscription);
            if (goalReachedAt != null && goalReachedAt.isBefore(periodEnd)) {
                periodEnd = goalReachedAt;
            }
        }
        // Days carried over from an earlier plan the supplier paid over early (see remainingDays).
        if (subscription.getCarryOverDays() > 0) {
            periodEnd = periodEnd.plus(subscription.getCarryOverDays(), ChronoUnit.DAYS);
        }

        Instant now = Instant.now();
        if (periodEnd.isBefore(now)) {
            // Past periodEnd doesn't mean fully cut off right away - the
            // grace period (SUBSCRIPTION_GRACE_PERIOD_DAYS) still treats the
            // vendor as having a live plan (plan() stays non-null, so
            // VendorSearchService#hasActiveSubscription and every
            // requireActiveSubscription() call keep working exactly as they
            // do for an active plan) until the grace window itself lapses.
            Instant graceEndsAt = periodEnd.plus(
                    systemSettingService.getInt(SystemSettingKey.SUBSCRIPTION_GRACE_PERIOD_DAYS), ChronoUnit.DAYS);
            if (graceEndsAt.isAfter(now)) {
                return new EffectivePlan(
                        subscription.getPlan(), periodEnd, false, false, true, graceEndsAt, subscription.getBillingSource());
            }
            return new EffectivePlan(null, periodEnd, false, true, false, null, subscription.getBillingSource());
        }

        boolean expiringSoon = periodEnd.isBefore(
                now.plus(systemSettingService.getInt(SystemSettingKey.SUBSCRIPTION_WARNING_DAYS), ChronoUnit.DAYS));
        return new EffectivePlan(
                subscription.getPlan(), periodEnd, expiringSoon, false, false, null, subscription.getBillingSource());
    }

    /** When the supplier's Nth counted booking was counted, or null while they are still below the goal. */
    private Instant goalReachedAt(VendorSubscription subscription) {
        int goal = subscription.getBookingsGoal();
        java.util.List<Object> times = bookingStatusEventRepository
                .countedBookingTimes(subscription.getUser().getId(), goal);
        if (times.size() < goal) {
            return null;
        }
        return toInstant(times.get(goal - 1));
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offset) {
            return offset.toInstant();
        }
        throw new IllegalStateException("Unexpected timestamp type: " + value.getClass());
    }

    /**
     * inGracePeriod/graceEndsAt are only ever set once expiresAt has already
     * passed but the grace window (see SUBSCRIPTION_GRACE_PERIOD_DAYS) hasn't
     * - mutually exclusive with expired, which only becomes true once the
     * grace window itself has also lapsed. plan() stays non-null throughout
     * the grace period, same as a normal active plan. billingSource is null
     * only when there's no subscription row at all (never subscribed) - the
     * paywall uses that, together with expiresAt() also being null and
     * every warning flag being false, to detect "needs plan selection."
     */
    public record EffectivePlan(
            PlanTier plan, Instant expiresAt, boolean expiringSoon, boolean expired, boolean inGracePeriod,
            Instant graceEndsAt, BillingSource billingSource) {
    }
}
