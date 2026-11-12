package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SignupIntent;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * The admin's manual "finish your sign-up" email to someone who chose "Become a Vendor" but never completed
 * onboarding (the admin Incomplete Sign-ups tab). Nothing is sent automatically: an admin clicks the button.
 * Each person gets at most {@value #MAX_REMINDERS} reminders, at least {@link #MIN_GAP} apart. The email comes
 * from, and replies go to, the EventsRUs support address.
 *
 * Email is only really sent when app.admin-notifications-enabled is true (Railway production); local runs
 * never send real email.
 *
 * Deliberately NOT one big transaction: each person is saved the moment their email goes out, so a slow or
 * failing send for one person can never lose, or hold up recording, the others.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncompleteSignupReminderService {

    static final int MAX_REMINDERS = 3;
    static final Duration MIN_GAP = Duration.ofDays(7);

    public enum Outcome { SENT, TOO_SOON, LIMIT_REACHED, NOT_ELIGIBLE, EMAIL_OFF, FAILED }

    /** What happened for one person; {@code email} is returned so the admin can see who it was about. */
    public record Result(Long userId, String email, Outcome outcome) {}

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${app.admin-notifications-enabled}")
    private boolean enabled;

    @Value("${app.support-email:support@eventsrus.app}")
    private String supportEmail;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    public Result sendTo(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return new Result(userId, null, Outcome.NOT_ELIGIBLE);
        }
        return send(user, Instant.now());
    }

    public List<Result> sendToAll() {
        Instant now = Instant.now();
        return userRepository.findBySignupIntentAndRoleNotOrderByCreatedAtDesc(SignupIntent.VENDOR, Role.VENDOR).stream()
                .map(user -> send(user, now))
                .toList();
    }

    private Result send(User user, Instant now) {
        Result result = attempt(user, now);
        log.info("Finish-your-sign-up email for user {}: {}", user.getId(), result.outcome());
        return result;
    }

    private Result attempt(User user, Instant now) {
        Outcome outcome = check(user, now);
        if (outcome != null) {
            return new Result(user.getId(), user.getEmail(), outcome);
        }
        if (!enabled) {
            return new Result(user.getId(), user.getEmail(), Outcome.EMAIL_OFF);
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(supportEmail, "EventsRUs");
            helper.setReplyTo(supportEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(SUBJECT);
            String link = frontendBaseUrl + "/vendor/";
            helper.setText(plainText(user.getFirstName(), link, supportEmail), html(user.getFirstName(), link, supportEmail));
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Could not send the finish-your-sign-up email to user {}", user.getId(), e);
            return new Result(user.getId(), user.getEmail(), Outcome.FAILED);
        }
        user.setSignupReminderCount(user.getSignupReminderCount() + 1);
        user.setSignupReminderLastSentAt(now);
        userRepository.save(user);
        return new Result(user.getId(), user.getEmail(), Outcome.SENT);
    }

    /** Null when this person may be emailed now; otherwise why not. */
    Outcome check(User user, Instant now) {
        boolean stillIncomplete = user.getSignupIntent() == SignupIntent.VENDOR && user.getRole() != Role.VENDOR;
        if (!stillIncomplete || user.getDeletedAt() != null || user.isFakeAccount()
                || user.getEmail() == null || user.getEmail().isBlank()) {
            return Outcome.NOT_ELIGIBLE;
        }
        if (user.getSignupReminderCount() >= MAX_REMINDERS) {
            return Outcome.LIMIT_REACHED;
        }
        Instant last = user.getSignupReminderLastSentAt();
        if (last != null && last.plus(MIN_GAP).isAfter(now)) {
            return Outcome.TOO_SOON;
        }
        return null;
    }

    static final String SUBJECT = "Finish your EventsRUs supplier sign-up - it only takes about 2 minutes";

    static String plainText(String firstName, String link, String supportEmail) {
        return "Hi " + greeting(firstName) + ",\n\n"
                + "You started signing up as a supplier on EventsRUs but haven't finished yet. The rest is quick and easy: "
                + "we only need some basic information about your business - your business name, the kind of service you offer, "
                + "and where you work.\n\n"
                + "Once you're done, planners can find you and send you quotation requests, and your free trial starts.\n\n"
                + "Finish your sign-up here: " + link + "\n\n"
                + "Need a hand? Just reply to this email or write to " + supportEmail + " and we'll help you.\n\n"
                + "If you'd rather not get emails like this, just reply and tell us - we won't send you another.\n\n"
                + "The EventsRUs team\n";
    }

    static String html(String firstName, String link, String supportEmail) {
        String name = HtmlUtils.htmlEscape(greeting(firstName));
        String url = HtmlUtils.htmlEscape(link);
        String support = HtmlUtils.htmlEscape(supportEmail);
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:16px;line-height:1.5;color:#14162b;max-width:520px\">"
                + "<p>Hi " + name + ",</p>"
                + "<p>You started signing up as a supplier on EventsRUs but haven't finished yet. The rest is quick and easy: "
                + "we only need some basic information about your business &ndash; your business name, the kind of service you offer, "
                + "and where you work.</p>"
                + "<p>Once you're done, planners can find you and send you quotation requests, and your free trial starts.</p>"
                + "<p style=\"margin:28px 0\"><a href=\"" + url + "\" style=\"background:#0c83ff;color:#ffffff;text-decoration:none;"
                + "padding:12px 22px;border-radius:8px;font-weight:bold;display:inline-block\">Finish my sign-up</a></p>"
                + "<p>Need a hand? Just reply to this email or write to <a href=\"mailto:" + support + "\">" + support
                + "</a> and we'll help you.</p>"
                + "<p style=\"color:#5b6472;font-size:14px\">If you'd rather not get emails like this, just reply and tell us &ndash; "
                + "we won't send you another.</p>"
                + "<p>The EventsRUs team</p></div>";
    }

    private static String greeting(String firstName) {
        return firstName == null || firstName.isBlank() ? "there" : firstName.trim();
    }
}
