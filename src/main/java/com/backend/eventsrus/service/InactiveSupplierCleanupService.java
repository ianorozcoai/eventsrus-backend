package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import com.backend.eventsrus.service.S3UploadService.Visibility;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cleanup for suppliers who simply stopped renewing (Ian's rule, 2026-10-16). A supplier counts as
 * lapsed when they have no live subscription. Their "last activity" is the latest of: last sign-in,
 * account creation, and the end of their most recent paid period. Then:
 * <ul>
 *   <li>12 months lapsed: the stored ID card and selfie files are removed (the most sensitive files)
 *       and the verified badge is dropped; the rest of the account is kept.</li>
 *   <li>24 months lapsed: an email warns that the account will be deleted in 30 days.</li>
 *   <li>30 days after the warning, still lapsed: the account is deleted exactly like "Delete account".
 *       Signing in or re-subscribing in that window cancels it (the warning is cleared).</li>
 * </ul>
 * Everything is OFF unless {@code app.inactivity-cleanup-enabled=true} (set only in production, on
 * purpose, so nothing is emailed or deleted by accident). Demo accounts ({@code fakeAccount}) are skipped.
 */
@Service
@Slf4j
public class InactiveSupplierCleanupService {

    private static final Set<SubscriptionStatus> LIVE_STATUSES = EnumSet.of(
            SubscriptionStatus.ACTIVE, SubscriptionStatus.APPROVED, SubscriptionStatus.PAYMENT_VERIFICATION);
    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final VendorSubscriptionRepository vendorSubscriptionRepository;
    private final AccountDeletionService accountDeletionService;
    private final S3UploadService s3UploadService;
    private final JavaMailSender mailSender;
    private final TransactionTemplate transactionTemplate;

    private final boolean enabled;
    private final int idFilesAfterMonths;
    private final int deleteAfterMonths;
    private final int warningDays;

    public InactiveSupplierCleanupService(
            UserRepository userRepository,
            VendorProfileRepository vendorProfileRepository,
            VendorSubscriptionRepository vendorSubscriptionRepository,
            AccountDeletionService accountDeletionService,
            S3UploadService s3UploadService,
            JavaMailSender mailSender,
            PlatformTransactionManager transactionManager,
            @Value("${app.inactivity-cleanup-enabled:false}") boolean enabled,
            @Value("${app.inactivity.id-files-after-months:12}") int idFilesAfterMonths,
            @Value("${app.inactivity.delete-after-months:24}") int deleteAfterMonths,
            @Value("${app.inactivity.warning-days:30}") int warningDays) {
        this.userRepository = userRepository;
        this.vendorProfileRepository = vendorProfileRepository;
        this.vendorSubscriptionRepository = vendorSubscriptionRepository;
        this.accountDeletionService = accountDeletionService;
        this.s3UploadService = s3UploadService;
        this.mailSender = mailSender;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.idFilesAfterMonths = idFilesAfterMonths;
        this.deleteAfterMonths = deleteAfterMonths;
        this.warningDays = warningDays;
    }

    /** Daily sweep. Does nothing (returns 0) unless the cleanup is switched on. */
    public int runOnce() {
        return runOnce(Instant.now());
    }

    int runOnce(Instant now) {
        if (!enabled) {
            return 0;
        }
        int actions = 0;
        for (User user : userRepository.findByRoleAndDeletedAtIsNull(Role.VENDOR)) {
            if (user.isFakeAccount()) {
                continue;
            }
            try {
                actions += sweep(user, now);
            } catch (RuntimeException e) {
                // One failing supplier must not block the rest; retried tomorrow.
                log.error("Lapsed-supplier cleanup failed for user id {}", user.getId(), e);
            }
        }
        return actions;
    }

    private int sweep(User user, Instant now) {
        if (hasLiveSubscription(user)) {
            return clearWarning(user) ? 1 : 0;
        }

        Instant lastActivity = lastActivity(user);
        Instant idFilesCutoff = months(now, idFilesAfterMonths);
        Instant deleteCutoff = months(now, deleteAfterMonths);
        int actions = 0;

        if (lastActivity.isBefore(idFilesCutoff) && user.getIdFilesPurgedAt() == null) {
            removeIdFiles(user.getId(), now);
            actions++;
        }

        if (!lastActivity.isBefore(deleteCutoff)) {
            // Activity resumed (e.g. signed in or renewed) since a warning went out.
            return actions + (clearWarning(user) ? 1 : 0);
        }

        Instant warnedAt = user.getInactivityWarningSentAt();
        if (warnedAt == null) {
            if (sendWarning(user, now)) {
                user.setInactivityWarningSentAt(now);
                userRepository.save(user);
                actions++;
            }
        } else if (warnedAt.isBefore(now.minus(Duration.ofDays(warningDays)))) {
            log.info("Deleting lapsed supplier account, user id {}", user.getId());
            accountDeletionService.purgeAccount(user.getId());
            actions++;
        }
        return actions;
    }

    private boolean hasLiveSubscription(User user) {
        return vendorSubscriptionRepository.existsByUserIdAndStatusIn(user.getId(), LIVE_STATUSES);
    }

    private Instant lastActivity(User user) {
        Instant latest = user.getCreatedAt() != null ? user.getCreatedAt() : Instant.EPOCH;
        if (user.getLastLoginAt() != null && user.getLastLoginAt().isAfter(latest)) {
            latest = user.getLastLoginAt();
        }
        Optional<VendorSubscription> subscription =
                vendorSubscriptionRepository.findFirstByUserIdOrderByCurrentPeriodStartDesc(user.getId());
        if (subscription.isPresent() && subscription.get().getCurrentPeriodEnd() != null
                && subscription.get().getCurrentPeriodEnd().isAfter(latest)) {
            latest = subscription.get().getCurrentPeriodEnd();
        }
        return latest;
    }

    private static Instant months(Instant now, int months) {
        return ZonedDateTime.ofInstant(now, MANILA).minusMonths(months).toInstant();
    }

    private boolean clearWarning(User user) {
        if (user.getInactivityWarningSentAt() == null) {
            return false;
        }
        user.setInactivityWarningSentAt(null);
        userRepository.save(user);
        return true;
    }

    /** Removes the stored ID card and selfie after 12 months of lapse; the supplier must re-verify if they return. */
    private void removeIdFiles(Long userId, Instant now) {
        List<String> keys = transactionTemplate.execute(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            Optional<VendorProfile> profile = vendorProfileRepository.findByUserId(userId);
            List<String> removed = new java.util.ArrayList<>();
            profile.ifPresent(p -> {
                if (p.getIdCardKey() != null) {
                    removed.add(p.getIdCardKey());
                }
                if (p.getSelfieKey() != null) {
                    removed.add(p.getSelfieKey());
                }
                p.setIdCardKey(null);
                p.setSelfieKey(null);
                p.setVerified(false);
                vendorProfileRepository.save(p);
            });
            user.setIdFilesPurgedAt(now);
            userRepository.save(user);
            return removed;
        });
        if (keys != null) {
            keys.forEach(key -> s3UploadService.deleteQuietly(key, Visibility.PRIVATE));
        }
        log.info("Removed stored ID files of lapsed supplier, user id {}", userId);
    }

    private boolean sendWarning(User user, Instant now) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return false;
        }
        String deletionDate = DATE_FORMAT.format(ZonedDateTime.ofInstant(now.plus(Duration.ofDays(warningDays)), MANILA));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Your EventsRUs supplier account will be deleted on " + deletionDate);
        message.setText("Hi " + (user.getFirstName() != null ? user.getFirstName() : "there") + ",\n\n"
                + "Your EventsRUs supplier account has had no active subscription and no sign-in for "
                + deleteAfterMonths + " months. To protect your privacy we will permanently delete it, "
                + "including your storefront, photos and documents, on " + deletionDate + ".\n\n"
                + "Want to keep it? Just sign in at https://eventsrus.app (or the EventsRUs app) before then. "
                + "Nothing will be deleted.\n\n"
                + "Bookings and quotations other people already have with you are kept without your name.\n\n"
                + "Questions? support@eventsrus.app\n\nThe EventsRUs team");
        try {
            mailSender.send(message);
            return true;
        } catch (RuntimeException e) {
            // Mail is down or misconfigured: do NOT start the 30 days without the person being told.
            log.error("Could not send lapsed-supplier warning to user id {}", user.getId(), e);
            return false;
        }
    }
}
