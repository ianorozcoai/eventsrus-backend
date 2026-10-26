package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.AdminExtendAccessResponse;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.exception.InvalidAccessExtensionException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.model.VendorSubscriptionEvent;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorSubscriptionEventRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin support tool: give one supplier more access time (a goodwill extension, a payment problem being
 * sorted out, a pilot supplier...). The days are added on top of wherever the supplier's access ends now,
 * or from today if it has already ended, using the same "carry-over days" that paying early uses, so it
 * works the same whether the plan came from PayPal, GCash, Google Play or a free grant and never fights
 * with their billing: renewals and webhooks keep working underneath. A supplier with no plan at all gets
 * a free PRO period instead. Every extension is written to the subscription event log (who, how many days,
 * why) and the supplier gets a notification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSubscriptionExtensionService {

    static final int MAX_DAYS = 365;
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH).withZone(ZoneId.of("Asia/Manila"));

    private final UserRepository userRepository;
    private final VendorSubscriptionRepository vendorSubscriptionRepository;
    private final VendorSubscriptionEventRepository vendorSubscriptionEventRepository;
    private final VendorPlanService vendorPlanService;
    private final NotificationService notificationService;

    @Transactional
    public AdminExtendAccessResponse extend(Long vendorUserId, int days, String reason, String adminLabel) {
        if (days < 1 || days > MAX_DAYS) {
            throw new InvalidAccessExtensionException("Enter a number of days between 1 and " + MAX_DAYS + ".");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidAccessExtensionException("A reason is required, it is kept in the supplier's history.");
        }
        User vendor = userRepository.findById(vendorUserId)
                .filter(u -> u.getRole() == Role.VENDOR)
                .orElseThrow(() -> new InvalidAccessExtensionException("Supplier not found."));

        Instant now = Instant.now();
        VendorSubscription latest = vendorSubscriptionRepository
                .findFirstByUserIdOrderByCurrentPeriodStartDesc(vendorUserId).orElse(null);
        if (latest != null && latest.getStatus() == SubscriptionStatus.PAYMENT_VERIFICATION) {
            throw new InvalidAccessExtensionException(
                    "A GCash payment from this supplier is waiting for review. Verify or reject it first.");
        }

        VendorSubscription row;
        if (latest == null || latest.getCurrentPeriodEnd() == null) {
            // Never subscribed (or only an unfinished/rejected payment attempt): start a free PRO period.
            row = vendorSubscriptionRepository.save(VendorSubscription.builder()
                    .user(vendor)
                    .plan(PlanTier.PRO)
                    .billingSource(BillingSource.FREE_GRANT)
                    .status(SubscriptionStatus.ACTIVE)
                    .currentPeriodStart(now)
                    .currentPeriodEnd(now.plus(days, ChronoUnit.DAYS))
                    .build());
        } else {
            Instant effectiveEnd = latest.getCurrentPeriodEnd().plus(latest.getCarryOverDays(), ChronoUnit.DAYS);
            long extra = days;
            if (effectiveEnd.isBefore(now)) {
                // Already ended: count the new days from today (not the old end date), to the nearest whole day.
                extra += daysBetweenRounded(effectiveEnd, now);
            }
            latest.setCarryOverDays(latest.getCarryOverDays() + (int) extra);
            row = vendorSubscriptionRepository.save(latest);
        }

        Instant newEnd = vendorPlanService.getEffectivePlan(vendorUserId).expiresAt();
        String who = adminLabel == null || adminLabel.isBlank() ? "admin" : adminLabel.trim();
        vendorSubscriptionEventRepository.save(VendorSubscriptionEvent.builder()
                .vendorSubscription(row)
                .paypalEventId("admin-extend-" + row.getId() + "-" + now.toEpochMilli())
                .eventType("ADMIN_EXTEND_ACCESS")
                .payload("by=" + who + "; days=" + days + "; reason=" + reason.trim()
                        + "; accessEndsAt=" + newEnd)
                .occurredAt(now)
                .build());
        log.info("Admin {} extended supplier {}'s access by {} days (reason: {}); now ends {}",
                who, vendorUserId, days, reason.trim(), newEnd);

        notificationService.notify(vendor, NotificationType.SUBSCRIPTION_EXTENDED,
                "Your access was extended",
                "Your supplier access now runs until " + DATE.format(newEnd) + ".",
                "SUBSCRIPTION", row.getId());

        return AdminExtendAccessResponse.builder()
                .vendorUserId(vendorUserId)
                .daysAdded(days)
                .accessEndsAt(newEnd)
                .billingSource(row.getBillingSource())
                .build();
    }

    private static long daysBetweenRounded(Instant from, Instant to) {
        long minutes = Duration.between(from, to).toMinutes();
        return (minutes + 720) / 1440;
    }
}
