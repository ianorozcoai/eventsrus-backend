package com.backend.eventsrus.service;

import com.backend.eventsrus.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Emails a fixed admin inbox whenever a brand-new planner account is
 * created (see UserService#createFromGoogle) or an existing account
 * finishes vendor onboarding for the first time (see
 * UserService#becomeVendor) - "new vendor" deliberately means a completed
 * onboarding, not just someone who picked the vendor door at signup, since
 * the admin vendors page already tracks that half-finished state on its
 * own "Incomplete Sign-ups" tab.
 *
 * enabled is only ever true in Railway's production environment -
 * app.admin-notifications-enabled has no default of true in any properties
 * file committed to this repo (see application.properties), so local/dev
 * runs never send real email no matter which profile is active.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.admin-notification-email}")
    private String adminEmail;

    @Value("${app.admin-notifications-enabled}")
    private boolean enabled;

    // @Async so a slow/unreachable SMTP server can never add latency to, or
    // fail, the signup/onboarding request that triggered it - the calling
    // transaction has already committed by the time this runs.
    @Async
    public void notifyNewPlanner(User user) {
        send("New planner signed up on EventsRUs",
                "A new planner account was just created.\n\n"
                        + "Name: " + displayName(user) + "\n"
                        + "Email: " + user.getEmail());
    }

    @Async
    public void notifyNewVendor(User user) {
        send("New vendor signed up on EventsRUs",
                "An account just finished vendor onboarding.\n\n"
                        + "Name: " + displayName(user) + "\n"
                        + "Email: " + user.getEmail());
    }

    // PayPal's own retry/dunning schedule handles the subscription itself
    // (it fires BILLING.SUBSCRIPTION.SUSPENDED/CANCELLED later if retries
    // are exhausted - see PayPalWebhookService) - this is just an early
    // heads-up so support can proactively follow up before that happens.
    @Async
    public void notifyPaymentFailed(User vendor) {
        send("PayPal subscription payment failed",
                "A vendor's subscription renewal payment failed.\n\n"
                        + "Name: " + displayName(vendor) + "\n"
                        + "Email: " + vendor.getEmail());
    }

    private String displayName(User user) {
        if (user.getFirstName() == null) {
            return user.getEmail();
        }
        return user.getLastName() != null ? user.getFirstName() + " " + user.getLastName() : user.getFirstName();
    }

    private void send(String subject, String body) {
        if (!enabled) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(adminEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            // Never let a notification failure surface anywhere - this
            // runs after the real work is already done.
            log.error("Failed to send admin notification email ('{}')", subject, e);
        }
    }
}
