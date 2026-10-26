package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.model.SubscriptionReminder;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.SubscriptionReminderRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Daily renewal reminders: one 14 days, one 7 days and one 3 days before a supplier's access ends -
 * never every day - each as an in-app/push notification AND an SMS (Semaphore). "Access ends" is what
 * the supplier is told: the end of the free trial / grace period, or the plan's end date.
 *
 * Skipped for suppliers whose plan renews by itself (an active PayPal or Google Play subscription)
 * and for GCash payments still under review. The SMS also needs a mobile number and
 * app.sms-enabled=true (nothing is sent or marked as sent otherwise); the notification does not.
 * Sent reminders are recorded per (supplier, end date, days before, channel) - paying moves the
 * end date, which starts the reminders again for the new date.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionReminderService {

    static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private final VendorSubscriptionRepository vendorSubscriptionRepository;
    private final SubscriptionReminderRepository subscriptionReminderRepository;
    private final UserRepository userRepository;
    private final VendorPlanService vendorPlanService;
    private final SmsService smsService;
    private final NotificationService notificationService;

    private static final String CHANNEL_SMS = "SMS";
    private static final String CHANNEL_PUSH = "PUSH";

    /** @return how many reminder SMS were sent. */
    public int runOnce() {
        Instant now = Instant.now();
        List<Long> userIds = vendorSubscriptionRepository.findUserIdsWithPeriodEndAfter(now.minus(400, ChronoUnit.DAYS));
        int sent = 0;
        for (Long userId : userIds) {
            try {
                if (remindIfDue(userId, now)) {
                    sent++;
                }
            } catch (Exception e) {
                // One supplier's problem must never stop the rest of the run.
                log.error("Subscription reminder failed for user {}", userId, e);
            }
        }
        if (sent > 0) {
            log.info("Subscription reminders sent: {}", sent);
        }
        return sent;
    }

    private boolean remindIfDue(Long userId, Instant now) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() != Role.VENDOR || user.isFakeAccount()
                || user.getDeletedAt() != null || user.getDeletionRequestedAt() != null) {
            return false;
        }
        VendorPlanService.EffectivePlan plan = vendorPlanService.getEffectivePlan(userId);
        if (plan.plan() == null || plan.expired()) {
            return false;
        }
        VendorSubscription latest = vendorSubscriptionRepository.findFirstByUserIdOrderByCurrentPeriodStartDesc(userId)
                .orElse(null);
        if (latest == null || latest.getStatus() == SubscriptionStatus.PAYMENT_VERIFICATION
                || (!plan.inGracePeriod() && renewsByItself(latest))) {
            return false;
        }

        Instant accessEnd = plan.inGracePeriod() ? plan.graceEndsAt() : plan.expiresAt();
        if (accessEnd == null) {
            return false;
        }
        int daysLeft = daysLeft(now, accessEnd);
        Integer bucket = bucketFor(daysLeft);
        if (bucket == null) {
            return false;
        }
        LocalDate endDate = accessEnd.atZone(MANILA).toLocalDate();
        boolean freeTrial = plan.billingSource() == BillingSource.FREE_GRANT;

        // Two channels, each tracked on its own so a failed or switched-off SMS never blocks the
        // in-app/push notification, and the other way round.
        boolean sent = false;
        if (!alreadySent(userId, endDate, bucket, CHANNEL_PUSH)) {
            notificationService.notify(user, NotificationType.SUBSCRIPTION_RENEWAL_REMINDER,
                    titleFor(freeTrial, daysLeft), bodyFor(freeTrial), "SUBSCRIPTION", latest.getId());
            record(userId, endDate, bucket, CHANNEL_PUSH);
            sent = true;
        }
        if (!alreadySent(userId, endDate, bucket, CHANNEL_SMS)
                && smsService.sendSubscriptionReminder(user, messageFor(freeTrial, daysLeft, endDate), latest.getId())) {
            record(userId, endDate, bucket, CHANNEL_SMS);
            sent = true;
        }
        return sent;
    }

    private boolean alreadySent(Long userId, LocalDate endDate, int bucket, String channel) {
        return subscriptionReminderRepository.existsByUserIdAndAccessEndDateAndLeadDaysAndChannel(
                userId, endDate, bucket, channel);
    }

    private void record(Long userId, LocalDate endDate, int bucket, String channel) {
        subscriptionReminderRepository.save(SubscriptionReminder.builder()
                .userId(userId).accessEndDate(endDate).leadDays(bucket).channel(channel).build());
    }

    static String titleFor(boolean freeTrial, int daysLeft) {
        String days = daysLeft + (daysLeft == 1 ? " day" : " days");
        return freeTrial ? "Your free trial ends in " + days : "Your PRO access ends in " + days;
    }

    static String bodyFor(boolean freeTrial) {
        return freeTrial
                ? "Choose a plan to keep your supplier tools."
                : "Renew to keep quotations, chat and bookings working.";
    }

    /** A plan that charges again by itself needs no renewal reminder. */
    private static boolean renewsByItself(VendorSubscription subscription) {
        return subscription.getStatus() == SubscriptionStatus.ACTIVE
                && (subscription.getBillingSource() == BillingSource.PAYPAL
                        || subscription.getBillingSource() == BillingSource.GOOGLE_PLAY);
    }

    /** Whole days left, rounded up (a plan ending in 3 hours has 1 day left). */
    static int daysLeft(Instant now, Instant accessEnd) {
        long minutes = Duration.between(now, accessEnd).toMinutes();
        return minutes <= 0 ? 0 : (int) ((minutes + 1439) / 1440);
    }

    /** 15+ days: none; 8-14: the "14 days" reminder; 4-7: the "7 days" one; 1-3: the "3 days" one. */
    static Integer bucketFor(int daysLeft) {
        if (daysLeft > 14 || daysLeft < 1) {
            return null;
        }
        if (daysLeft > 7) {
            return 14;
        }
        return daysLeft > 3 ? 7 : 3;
    }

    static String messageFor(boolean freeTrial, int daysLeft, LocalDate endDate) {
        String days = daysLeft + (daysLeft == 1 ? " day" : " days");
        String when = endDate.format(DATE);
        return freeTrial
                ? "EventsRUs: your free trial ends in " + days + " (" + when
                        + "). Choose a plan in the app to keep your supplier tools."
                : "EventsRUs: your PRO access ends in " + days + " (" + when
                        + "). Renew in the app to keep your supplier tools.";
    }
}
