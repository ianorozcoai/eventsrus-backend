package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.model.VendorSubscriptionEvent;
import com.backend.eventsrus.repository.VendorSubscriptionEventRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayPalWebhookService {

    private final VendorSubscriptionRepository vendorSubscriptionRepository;
    private final VendorSubscriptionEventRepository vendorSubscriptionEventRepository;
    private final PayPalSubscriptionClient payPalSubscriptionClient;
    private final VendorBillingHistoryService vendorBillingHistoryService;
    private final VendorReferralService vendorReferralService;

    @Transactional
    public void handle(JsonNode event) {
        String eventId = event.path("id").asString(null);
        String eventType = event.path("event_type").asString(null);

        if (eventId == null || eventType == null) {
            log.warn("Ignoring malformed PayPal webhook payload (missing id/event_type)");
            return;
        }

        if (vendorSubscriptionEventRepository.existsByPaypalEventId(eventId)) {
            log.info("Ignoring duplicate PayPal webhook redelivery: {}", eventId);
            return;
        }

        VendorSubscription subscription = resolveSubscription(event, eventType);
        if (subscription != null) {
            applyTransition(subscription, eventType, event.path("resource"));
        } else {
            log.warn("Could not map PayPal webhook {} ({}) to a known subscription", eventId, eventType);
        }

        vendorSubscriptionEventRepository.save(
                VendorSubscriptionEvent.builder()
                        .vendorSubscription(subscription)
                        .paypalEventId(eventId)
                        .eventType(eventType)
                        .payload(event.toString())
                        .occurredAt(Instant.now())
                        .build());
    }

    private void applyTransition(VendorSubscription subscription, String eventType, JsonNode resource) {
        switch (eventType) {
            case "BILLING.SUBSCRIPTION.ACTIVATED" -> {
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                subscription.setCurrentPeriodStart(Instant.now());
                refreshPeriodEndFromPayPal(subscription);
            }
            case "PAYMENT.SALE.COMPLETED" -> {
                // The period this payment covers runs from wherever the
                // last one left off (or the subscription's original start,
                // for the very first payment) through the newly-refreshed
                // end - captured before the refresh overwrites it.
                Instant periodStart = subscription.getCurrentPeriodEnd() != null
                        ? subscription.getCurrentPeriodEnd()
                        : subscription.getCurrentPeriodStart();
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                refreshPeriodEndFromPayPal(subscription);
                recordPaymentHistory(subscription, resource, periodStart);
                // No-op unless this vendor has a still-PENDING referral (see
                // VendorReferralService#convertIfPending) - renewal payments
                // fire this same event but never re-convert or re-award.
                vendorReferralService.convertIfPending(subscription.getUser().getId());
            }
            case "BILLING.SUBSCRIPTION.CANCELLED" -> subscription.setStatus(SubscriptionStatus.CANCELLED);
            // Deliberately NOT touching currentPeriodEnd here — the vendor keeps
            // access until the already-paid-for period ends.
            case "BILLING.SUBSCRIPTION.SUSPENDED" -> subscription.setStatus(SubscriptionStatus.SUSPENDED);
            case "BILLING.SUBSCRIPTION.EXPIRED" -> subscription.setStatus(SubscriptionStatus.EXPIRED);
            default -> {
                // Logged for audit via the event row regardless; no state transition
                // acted on for this pass (e.g. BILLING.SUBSCRIPTION.UPDATED, PAYMENT.FAILED).
            }
        }
        vendorSubscriptionRepository.save(subscription);
    }

    private void recordPaymentHistory(VendorSubscription subscription, JsonNode resource, Instant periodStart) {
        String transactionId = resource.path("id").asString(null);
        String amountText = resource.path("amount").path("total").asString(null);
        String currency = resource.path("amount").path("currency").asString(null);
        BigDecimal amount = amountText != null ? new BigDecimal(amountText) : null;

        Instant periodEnd = subscription.getCurrentPeriodEnd() != null ? subscription.getCurrentPeriodEnd() : periodStart;
        vendorBillingHistoryService.recordPayment(
                subscription, amount, currency, transactionId, periodStart, periodEnd, Instant.now(), BillingSource.PAYPAL);
    }

    private void refreshPeriodEndFromPayPal(VendorSubscription subscription) {
        PayPalSubscriptionClient.PayPalSubscriptionDetails details =
                payPalSubscriptionClient.getSubscription(subscription.getPaypalSubscriptionId());
        if (details.nextBillingTime() != null) {
            subscription.setCurrentPeriodEnd(details.nextBillingTime());
        }
    }

    private VendorSubscription resolveSubscription(JsonNode event, String eventType) {
        JsonNode resource = event.path("resource");
        String paypalSubscriptionId = "PAYMENT.SALE.COMPLETED".equals(eventType)
                ? resource.path("billing_agreement_id").asString(null)
                : resource.path("id").asString(null);

        if (paypalSubscriptionId == null) {
            return null;
        }
        return vendorSubscriptionRepository.findByPaypalSubscriptionId(paypalSubscriptionId).orElse(null);
    }
}
