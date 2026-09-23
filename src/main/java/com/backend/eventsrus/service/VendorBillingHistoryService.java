package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.VendorBillingHistoryEntryResponse;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.model.VendorBillingHistoryEntry;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorBillingHistoryEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The vendor-facing "billing history" - every free-trial grant and every
 * PayPal payment (initial or renewal), each snapshotted with the plan and
 * period it applied to. See VendorBillingHistoryEntry for why this can't
 * just be read off VendorSubscription directly.
 */
@Service
@RequiredArgsConstructor
public class VendorBillingHistoryService {

    private final VendorBillingHistoryEntryRepository vendorBillingHistoryEntryRepository;
    private final UserRepository userRepository;

    @Transactional
    public void recordFreeGrant(
            VendorSubscription subscription, Instant periodStart, Instant periodEnd, BillingSource billingSource) {
        vendorBillingHistoryEntryRepository.save(VendorBillingHistoryEntry.builder()
                .vendorSubscription(subscription)
                .plan(subscription.getPlan())
                .billingSource(billingSource)
                .amount(null)
                .currency(null)
                .paypalTransactionId(null)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .occurredAt(periodStart)
                .build());
    }

    /**
     * Idempotent on paypalTransactionId - PayPal redelivers webhooks, and
     * PayPalWebhookService's own top-level dedup is keyed on the *webhook*
     * event id, not the underlying transaction, so this is a second,
     * belt-and-suspenders guard against double-recording the same payment.
     */
    @Transactional
    public void recordPayment(
            VendorSubscription subscription, BigDecimal amount, String currency, String paypalTransactionId,
            Instant periodStart, Instant periodEnd, Instant occurredAt, BillingSource billingSource) {
        if (paypalTransactionId != null && vendorBillingHistoryEntryRepository.existsByPaypalTransactionId(paypalTransactionId)) {
            return;
        }
        vendorBillingHistoryEntryRepository.save(VendorBillingHistoryEntry.builder()
                .vendorSubscription(subscription)
                .plan(subscription.getPlan())
                .billingSource(billingSource)
                .amount(amount)
                .currency(currency)
                .paypalTransactionId(paypalTransactionId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .occurredAt(occurredAt)
                .build());
    }

    /**
     * Drops the temporary-access "free grant" entry recordFreeGrant created
     * when this same subscription's GCash screenshot was first submitted -
     * called from VendorSubscriptionService#verifyGcashPayment once the real,
     * amount-bearing payment for it has just been recorded, so the vendor's
     * billing history shows only the one real record, not both the
     * temporary grant and the payment that superseded it.
     */
    @Transactional
    public void removeFreeGrant(VendorSubscription subscription) {
        vendorBillingHistoryEntryRepository.deleteByVendorSubscriptionAndAmountIsNull(subscription);
    }

    @Transactional(readOnly = true)
    public List<VendorBillingHistoryEntryResponse> listForVendor(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
        return vendorBillingHistoryEntryRepository.findByVendorSubscription_User_IdOrderByOccurredAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private VendorBillingHistoryEntryResponse toResponse(VendorBillingHistoryEntry entry) {
        return VendorBillingHistoryEntryResponse.builder()
                .id(entry.getId())
                .plan(entry.getPlan())
                .billingSource(entry.getBillingSource())
                .amount(entry.getAmount())
                .currency(entry.getCurrency())
                .paypalTransactionId(entry.getPaypalTransactionId())
                .periodStart(entry.getPeriodStart())
                .periodEnd(entry.getPeriodEnd())
                .occurredAt(entry.getOccurredAt())
                .build();
    }
}
