package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.SmsLogResponse;
import com.backend.eventsrus.enums.SmsTriggerType;
import com.backend.eventsrus.model.SmsLog;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.SmsLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends SMS via Semaphore for two purposes: (1) an admin ad-hoc tool (any
 * number, any message - see AdminSmsController), and (2) vendor-facing
 * quotation/booking lifecycle alerts (see the trigger call sites in
 * QuotationService/BookingService/BookingAmendmentService). Deliberately
 * never SMSes planners or in-app chat messages - see the "Vendor SMS events"
 * scoping decision this was built against.
 *
 * app.sms-enabled gates everything the same way
 * AdminNotificationEmailService's own enabled flag does: it has no default
 * of true anywhere committed to this repo, so no real send happens until
 * it's explicitly turned on once Semaphore approves the "EventsRUsPH" sender
 * name. The triggered methods silently no-op while disabled (matching
 * AdminNotificationEmailService exactly, no log row written - these fire
 * constantly and logging every no-op before launch would just be noise).
 * The admin ad-hoc send is different: a human is actively trying to do
 * something right now, so it always returns a real, logged outcome instead
 * of silently doing nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsService {

    private final SemaphoreSmsClient semaphoreSmsClient;
    private final SmsLogRepository smsLogRepository;

    @Value("${app.sms-enabled}")
    private boolean enabled;

    @Async
    public void notifyVendorQuotationRequest(User vendor, User planner, Long quotationId) {
        if (!enabled) {
            return;
        }
        sendAndLog(vendor.getMobileNumber(),
                displayName(planner) + " sent you a new quotation request on EventsRUs. Open the app to respond.",
                SmsTriggerType.VENDOR_QUOTATION_REQUEST, null, "QUOTATION", quotationId);
    }

    @Async
    public void notifyVendorPaymentScreenshot(User vendor, User planner, String relatedEntityType, Long relatedEntityId) {
        if (!enabled) {
            return;
        }
        sendAndLog(vendor.getMobileNumber(),
                displayName(planner) + " uploaded a payment screenshot for your review on EventsRUs.",
                SmsTriggerType.VENDOR_PAYMENT_SCREENSHOT, null, relatedEntityType, relatedEntityId);
    }

    @Async
    public void notifyVendorAmendmentProposed(User vendor, User planner, Long bookingId) {
        if (!enabled) {
            return;
        }
        sendAndLog(vendor.getMobileNumber(),
                displayName(planner) + " proposed a change to a booking on EventsRUs. Open the app to respond.",
                SmsTriggerType.VENDOR_AMENDMENT_PROPOSED, null, "BOOKING", bookingId);
    }

    /**
     * Renewal reminder (see SubscriptionReminderService). Synchronous and returns whether the SMS
     * really went out, so the caller only marks the reminder as sent when it did: while SMS is
     * switched off, or the supplier has no mobile number, nothing is sent and nothing is logged,
     * and the reminder is tried again on the next daily run.
     */
    public boolean sendSubscriptionReminder(User vendor, String message, Long subscriptionId) {
        if (!enabled || vendor.getMobileNumber() == null || vendor.getMobileNumber().isBlank()) {
            return false;
        }
        return sendAndLog(vendor.getMobileNumber(), message, SmsTriggerType.SUBSCRIPTION_REMINDER, null,
                "SUBSCRIPTION", subscriptionId).isSuccess();
    }

    /** Synchronous (unlike the trigger methods above) - the admin UI needs a real success/failure right away. */
    public SmsLogResponse sendAdhoc(String adminEmail, String number, String message) {
        if (!enabled) {
            return toResponse(persist(number, message, SmsTriggerType.ADMIN_ADHOC, adminEmail, null, null,
                    false, "SMS sending is disabled (app.sms-enabled=false) - Semaphore sender name not yet approved"));
        }
        return toResponse(sendAndLog(number, message, SmsTriggerType.ADMIN_ADHOC, adminEmail, null, null));
    }

    public List<SmsLogResponse> recentHistory() {
        return smsLogRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    private SmsLog sendAndLog(String number, String message, SmsTriggerType type, String adminEmail,
            String relatedEntityType, Long relatedEntityId) {
        if (number == null || number.isBlank()) {
            return persist(number, message, type, adminEmail, relatedEntityType, relatedEntityId,
                    false, "No mobile number on file for this recipient");
        }
        try {
            semaphoreSmsClient.send(number, message);
            return persist(number, message, type, adminEmail, relatedEntityType, relatedEntityId, true, null);
        } catch (Exception e) {
            log.error("Failed to send SMS ({}) to {}", type, number, e);
            return persist(number, message, type, adminEmail, relatedEntityType, relatedEntityId,
                    false, e.getMessage());
        }
    }

    private SmsLog persist(String number, String message, SmsTriggerType type, String adminEmail,
            String relatedEntityType, Long relatedEntityId, boolean success, String errorMessage) {
        return smsLogRepository.save(SmsLog.builder()
                .recipientNumber(number)
                .message(message)
                .triggerType(type)
                .success(success)
                .errorMessage(errorMessage)
                .sentByAdminEmail(adminEmail)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .build());
    }

    private SmsLogResponse toResponse(SmsLog smsLog) {
        return SmsLogResponse.builder()
                .id(smsLog.getId())
                .recipientNumber(smsLog.getRecipientNumber())
                .message(smsLog.getMessage())
                .triggerType(smsLog.getTriggerType())
                .success(smsLog.isSuccess())
                .errorMessage(smsLog.getErrorMessage())
                .sentByAdminEmail(smsLog.getSentByAdminEmail())
                .createdAt(smsLog.getCreatedAt())
                .build();
    }

    private String displayName(User user) {
        if (user.getFirstName() == null) {
            return user.getEmail();
        }
        return user.getLastName() != null ? user.getFirstName() + " " + user.getLastName() : user.getFirstName();
    }
}
