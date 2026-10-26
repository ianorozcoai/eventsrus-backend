package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.BillingCycle;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.model.VendorSubscriptionEvent;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorSubscriptionEventRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Turns RevenueCat webhook events (Google Play purchases) into the same
 * VendorSubscription + billing-history rows PayPal produces, so
 * VendorPlanService gates access without knowing where the money came from.
 *
 * The existing vendor_subscription_events table is reused for dedup: its
 * "paypal_event_id" column simply holds the RevenueCat event id here (both
 * are opaque unique strings), and the Play transaction id goes in the
 * billing history's "paypal_transaction_id" column the same way.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueCatWebhookService {

    private final UserRepository userRepository;
    private final VendorSubscriptionRepository vendorSubscriptionRepository;
    private final VendorSubscriptionEventRepository vendorSubscriptionEventRepository;
    private final VendorBillingHistoryService vendorBillingHistoryService;
    private final VendorReferralService vendorReferralService;
    private final NotificationService notificationService;
    private final VendorPlanService vendorPlanService;

    @Transactional
    public void handle(JsonNode body) {
        JsonNode event = body.path("event");
        String eventId = event.path("id").asString(null);
        String type = event.path("type").asString(null);
        if (eventId == null || type == null) {
            log.warn("Ignoring malformed RevenueCat webhook payload (missing event id/type)");
            return;
        }
        if (vendorSubscriptionEventRepository.existsByPaypalEventId(eventId)) {
            log.info("Ignoring duplicate RevenueCat webhook redelivery: {}", eventId);
            return;
        }

        VendorSubscription subscription = null;
        if (!"TEST".equals(type)) {
            Optional<User> user = resolveUser(event);
            if (user.isPresent()) {
                subscription = apply(user.get(), type, event);
            } else {
                log.warn("Could not map RevenueCat event {} ({}) to a known user", eventId, type);
            }
        }

        vendorSubscriptionEventRepository.save(VendorSubscriptionEvent.builder()
                .vendorSubscription(subscription)
                .paypalEventId(eventId)
                .eventType("REVENUECAT_" + type)
                .payload(body.toString())
                .occurredAt(Instant.now())
                .build());
    }

    private VendorSubscription apply(User user, String type, JsonNode event) {
        Instant purchasedAt = millis(event.path("purchased_at_ms"));
        Instant expiresAt = millis(event.path("expiration_at_ms"));
        Optional<VendorSubscription> existing = vendorSubscriptionRepository
                .findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(user.getId(), BillingSource.GOOGLE_PLAY);

        switch (type) {
            case "INITIAL_PURCHASE", "RENEWAL", "UNCANCELLATION", "PRODUCT_CHANGE" -> {
                if (expiresAt == null) {
                    log.warn("RevenueCat {} event for user {} has no expiration; ignored", type, user.getId());
                    return existing.orElse(null);
                }
                // Paying early: days left on the previous plan (promo, GCash...) are added on top of this
                // purchase. Read before the row is touched. Skipped when this very Google Play plan is
                // still running, so a repeated INITIAL_PURCHASE can never count the same days twice.
                boolean playPlanStillRunning = existing
                        .map(row -> row.getCurrentPeriodEnd() != null
                                && row.getCurrentPeriodEnd().plus(row.getCarryOverDays(), ChronoUnit.DAYS)
                                        .isAfter(Instant.now()))
                        .orElse(false);
                int carryOverDays = "INITIAL_PURCHASE".equals(type) && !playPlanStillRunning
                        ? vendorPlanService.remainingDays(user.getId())
                        : -1;
                VendorSubscription sub = existing.orElseGet(() -> newRow(user));
                if (carryOverDays >= 0) {
                    sub.setCarryOverDays(carryOverDays);
                }
                Instant previousEnd = sub.getCurrentPeriodEnd();
                Instant start = purchasedAt != null ? purchasedAt : Instant.now();
                sub.setStatus(SubscriptionStatus.ACTIVE);
                sub.setCurrentPeriodStart(start);
                // A late or out-of-order delivery must not shorten a period we already know about.
                sub.setCurrentPeriodEnd(previousEnd != null && previousEnd.isAfter(expiresAt) ? previousEnd : expiresAt);
                BillingCycle cycle = cycleFor(event.path("product_id").asString(null), start, expiresAt);
                if (cycle != null) {
                    sub.setBillingCycle(cycle);
                }
                sub = vendorSubscriptionRepository.save(sub);
                if ("INITIAL_PURCHASE".equals(type) || "RENEWAL".equals(type)) {
                    recordPayment(sub, event, start, expiresAt);
                    // No-op unless the supplier has a still-PENDING referral.
                    vendorReferralService.convertIfPending(user.getId());
                }
                return sub;
            }
            case "CANCELLATION" -> {
                // Auto-renew switched off (or refunded): access runs until the
                // paid period ends, so only the status changes. A refund arrives
                // with an expiration already in the past, which this also honours.
                return existing.map(sub -> {
                    sub.setStatus(SubscriptionStatus.CANCELLED);
                    if (expiresAt != null) {
                        sub.setCurrentPeriodEnd(expiresAt);
                    }
                    return vendorSubscriptionRepository.save(sub);
                }).orElse(null);
            }
            case "EXPIRATION" -> {
                return existing.map(sub -> {
                    sub.setStatus(SubscriptionStatus.EXPIRED);
                    if (expiresAt != null) {
                        sub.setCurrentPeriodEnd(expiresAt);
                    }
                    return vendorSubscriptionRepository.save(sub);
                }).orElse(null);
            }
            case "BILLING_ISSUE" -> {
                // Google retries the charge during its own grace period and
                // RevenueCat sends EXPIRATION if it finally fails, so status
                // stays as is; this is only a heads-up to the supplier.
                existing.ifPresent(sub -> notificationService.notify(
                        user, NotificationType.SUBSCRIPTION_PAYMENT_FAILED,
                        "Subscription payment failed",
                        "Google Play couldn't process your last PRO subscription payment. Please update your "
                                + "payment method in Google Play to avoid losing access.",
                        "SUBSCRIPTION", sub.getId()));
                return existing.orElse(null);
            }
            default -> {
                log.info("RevenueCat event type {} logged, no state change", type);
                return existing.orElse(null);
            }
        }
    }

    private VendorSubscription newRow(User user) {
        return VendorSubscription.builder()
                .user(user)
                .plan(PlanTier.PRO)
                .billingSource(BillingSource.GOOGLE_PLAY)
                .status(SubscriptionStatus.ACTIVE)
                .build();
    }

    private void recordPayment(VendorSubscription sub, JsonNode event, Instant start, Instant end) {
        BigDecimal amount = null;
        String currency = null;
        JsonNode local = event.path("price_in_purchased_currency");
        if (local.isNumber()) {
            amount = local.decimalValue();
            currency = event.path("currency").asString(null);
        } else if (event.path("price").isNumber()) {
            // "price" is always in USD on RevenueCat events.
            amount = event.path("price").decimalValue();
            currency = "USD";
        }
        vendorBillingHistoryService.recordPayment(
                sub, amount, currency, event.path("transaction_id").asString(null),
                start, end, start, BillingSource.GOOGLE_PLAY);
    }

    /** RevenueCat app_user_id is our backend user id; fall back to a numeric alias after an anonymous-id merge. */
    private Optional<User> resolveUser(JsonNode event) {
        Long id = parseId(event.path("app_user_id").asString(null));
        if (id == null) {
            for (JsonNode alias : event.path("aliases")) {
                id = parseId(alias.asString(null));
                if (id != null) {
                    break;
                }
            }
        }
        return id == null ? Optional.empty() : userRepository.findById(id);
    }

    private static Long parseId(String raw) {
        if (raw == null || !raw.matches("\\d{1,18}")) {
            return null;
        }
        return Long.valueOf(raw);
    }

    private static Instant millis(JsonNode node) {
        return node.isNumber() ? Instant.ofEpochMilli(node.asLong()) : null;
    }

    /** Play product ids look like "supplier_pro:quarterly"; the part after the colon names the base plan. */
    static BillingCycle cycleFor(String productId, Instant start, Instant end) {
        if (productId != null) {
            String id = productId.toLowerCase(Locale.ROOT);
            if (id.contains("semi")) {
                return BillingCycle.SEMI_ANNUAL;
            }
            if (id.contains("quarter")) {
                return BillingCycle.QUARTERLY;
            }
            if (id.contains("annual") || id.contains("year")) {
                return BillingCycle.ANNUAL;
            }
            if (id.contains("month")) {
                return BillingCycle.MONTHLY;
            }
        }
        if (start == null || end == null) {
            return null;
        }
        long days = Duration.between(start, end).toDays();
        if (days >= 300) {
            return BillingCycle.ANNUAL;
        }
        if (days >= 150) {
            return BillingCycle.SEMI_ANNUAL;
        }
        if (days >= 60) {
            return BillingCycle.QUARTERLY;
        }
        return days > 0 ? BillingCycle.MONTHLY : null;
    }
}
