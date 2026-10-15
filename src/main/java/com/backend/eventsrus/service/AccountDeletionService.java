package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.exception.AccountDeletionBlockedException;
import com.backend.eventsrus.model.RefreshToken;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorGalleryPhoto;
import com.backend.eventsrus.model.VendorLegalDocument;
import com.backend.eventsrus.model.VendorPackageImage;
import com.backend.eventsrus.model.VendorPackage;
import com.backend.eventsrus.model.VendorPaymentMethod;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.DeviceTokenRepository;
import com.backend.eventsrus.repository.EventBookmarkedVendorRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.EventSuggestedVendorRepository;
import com.backend.eventsrus.repository.GoogleCalendarConnectionRepository;
import com.backend.eventsrus.repository.NotificationRepository;
import com.backend.eventsrus.repository.RefreshTokenRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorGalleryPhotoRepository;
import com.backend.eventsrus.repository.VendorLegalDocumentRepository;
import com.backend.eventsrus.repository.VendorPackageImageRepository;
import com.backend.eventsrus.repository.VendorPackageRepository;
import com.backend.eventsrus.repository.VendorPaymentMethodRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorReviewRepository;
import com.backend.eventsrus.repository.VendorSocialMediaLinkRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import com.backend.eventsrus.service.S3UploadService.Visibility;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Account deletion (a Google Play requirement). Flow:
 * <ol>
 *   <li>{@link #requestDeletion}: the user confirms in the app; the account is scheduled for deletion
 *       30 days out, push tokens and sessions are dropped. Signing in again within the grace period
 *       cancels it (see UserService#findOrCreateFromGoogle).</li>
 *   <li>{@link #purgeDueAccounts} (daily): scrubs every account whose grace period has run out.</li>
 * </ol>
 * The users row is kept as an anonymous shell, because bookings, quotations, billing history etc.
 * reference users(id). Its google_id is replaced, so signing in with the same Google account later
 * creates a brand-new account (a fresh start).
 */
@Service
@Slf4j
public class AccountDeletionService {

    public static final Duration GRACE_PERIOD = Duration.ofDays(30);

    // A supplier with one of these subscriptions must cancel it first.
    private static final Set<SubscriptionStatus> BLOCKING_SUBSCRIPTION_STATUSES = EnumSet.of(
            SubscriptionStatus.ACTIVE, SubscriptionStatus.APPROVED, SubscriptionStatus.PAYMENT_VERIFICATION);

    // A booking in any of these states, still in the future, must be cancelled first.
    private static final Set<BookingStatus> LIVE_BOOKING_STATUSES = EnumSet.of(
            BookingStatus.APPROVED,
            BookingStatus.AWAITING_PAYMENT,
            BookingStatus.PAYMENT_SUBMITTED,
            BookingStatus.PAYMENT_REJECTED,
            BookingStatus.BOOKED);

    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final VendorSubscriptionRepository vendorSubscriptionRepository;
    private final BookingRepository bookingRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRepository notificationRepository;
    private final VendorReviewRepository vendorReviewRepository;
    private final EventRepository eventRepository;
    private final GoogleCalendarConnectionRepository googleCalendarConnectionRepository;
    private final VendorGalleryPhotoRepository galleryPhotoRepository;
    private final VendorPackageImageRepository packageImageRepository;
    private final VendorLegalDocumentRepository legalDocumentRepository;
    private final VendorPaymentMethodRepository paymentMethodRepository;
    private final VendorSocialMediaLinkRepository socialMediaLinkRepository;
    private final VendorPackageRepository vendorPackageRepository;
    private final EventSuggestedVendorRepository eventSuggestedVendorRepository;
    private final EventBookmarkedVendorRepository eventBookmarkedVendorRepository;
    private final S3UploadService s3UploadService;
    private final TransactionTemplate transactionTemplate;

    public AccountDeletionService(
            UserRepository userRepository,
            VendorProfileRepository vendorProfileRepository,
            VendorSubscriptionRepository vendorSubscriptionRepository,
            BookingRepository bookingRepository,
            RefreshTokenRepository refreshTokenRepository,
            DeviceTokenRepository deviceTokenRepository,
            NotificationRepository notificationRepository,
            VendorReviewRepository vendorReviewRepository,
            EventRepository eventRepository,
            GoogleCalendarConnectionRepository googleCalendarConnectionRepository,
            VendorGalleryPhotoRepository galleryPhotoRepository,
            VendorPackageImageRepository packageImageRepository,
            VendorLegalDocumentRepository legalDocumentRepository,
            VendorPaymentMethodRepository paymentMethodRepository,
            VendorSocialMediaLinkRepository socialMediaLinkRepository,
            VendorPackageRepository vendorPackageRepository,
            EventSuggestedVendorRepository eventSuggestedVendorRepository,
            EventBookmarkedVendorRepository eventBookmarkedVendorRepository,
            S3UploadService s3UploadService,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.vendorProfileRepository = vendorProfileRepository;
        this.vendorSubscriptionRepository = vendorSubscriptionRepository;
        this.bookingRepository = bookingRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.notificationRepository = notificationRepository;
        this.vendorReviewRepository = vendorReviewRepository;
        this.eventRepository = eventRepository;
        this.googleCalendarConnectionRepository = googleCalendarConnectionRepository;
        this.galleryPhotoRepository = galleryPhotoRepository;
        this.packageImageRepository = packageImageRepository;
        this.legalDocumentRepository = legalDocumentRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.socialMediaLinkRepository = socialMediaLinkRepository;
        this.vendorPackageRepository = vendorPackageRepository;
        this.eventSuggestedVendorRepository = eventSuggestedVendorRepository;
        this.eventBookmarkedVendorRepository = eventBookmarkedVendorRepository;
        this.s3UploadService = s3UploadService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Schedules the caller's account for deletion in 30 days. Returns when it will be scrubbed. */
    @Transactional
    public Instant requestDeletion(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (user.getRole() == Role.ADMIN) {
            throw new AccountDeletionBlockedException("Admin accounts can't be deleted here.");
        }
        if (user.getDeletedAt() != null) {
            throw new AccountDeletionBlockedException("This account has already been deleted.");
        }
        if (vendorSubscriptionRepository.existsByUserIdAndStatusIn(user.getId(), BLOCKING_SUBSCRIPTION_STATUSES)) {
            throw new AccountDeletionBlockedException(
                    "You have an active subscription. Please cancel it first (PayPal, GCash or Google Play), "
                            + "then delete your account. You can cancel instead of deleting if you may come back: "
                            + "your data stays safe.");
        }
        if (bookingRepository.countLiveUpcomingBookingsFor(user.getId(), LIVE_BOOKING_STATUSES, Instant.now()) > 0) {
            throw new AccountDeletionBlockedException(
                    "You have an upcoming booking. Please cancel it first, then delete your account.");
        }

        Instant requestedAt = user.getDeletionRequestedAt() != null ? user.getDeletionRequestedAt() : Instant.now();
        user.setDeletionRequestedAt(requestedAt);
        userRepository.save(user);

        // End every session and stop pushes right away; signing in again cancels the deletion.
        List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId());
        tokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);
        deviceTokenRepository.deleteByUserId(user.getId());

        return requestedAt.plus(GRACE_PERIOD);
    }

    /** Runs once a day: scrubs every account whose 30-day grace period is over. Returns how many. */
    public int purgeDueAccounts() {
        List<User> due = userRepository.findByDeletionRequestedAtBeforeAndDeletedAtIsNull(
                Instant.now().minus(GRACE_PERIOD));
        int purged = 0;
        for (User user : due) {
            Long userId = user.getId();
            try {
                purgeAccount(userId);
                purged++;
            } catch (RuntimeException e) {
                // One failing account must not block the others; it is retried tomorrow.
                log.error("Account deletion failed for user id {}", userId, e);
            }
        }
        return purged;
    }

    /**
     * Scrubs one account right now in its own transaction, then removes its stored files. Used by the
     * daily sweep above and by InactiveSupplierCleanupService (whose 30-day email warning plays the
     * role of the grace period).
     */
    public void purgeAccount(Long userId) {
        StoredFiles files = transactionTemplate.execute(status -> purge(userId));
        deleteFiles(files);
    }

    /** Scrubs one account inside the caller's transaction. Returns the stored files to delete after commit. */
    StoredFiles purge(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        if (user.getDeletedAt() != null) {
            return new StoredFiles(List.of(), List.of());
        }

        List<String> publicUrls = new ArrayList<>();
        List<String> privateKeys = new ArrayList<>();

        vendorProfileRepository.findByUserId(userId).ifPresent(profile -> scrubVendorProfile(profile, publicUrls, privateKeys));

        // Chat messages are deliberately KEPT (Ian's decision: the other person's conversation history
        // must not develop holes). Their sender shows as "Deleted user" because the users row is scrubbed.
        vendorReviewRepository.scrubCommentsWrittenBy(userId);
        eventRepository.scrubEventsOf(userId);
        notificationRepository.deleteByRecipientId(userId);
        deviceTokenRepository.deleteByUserId(userId);
        List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
        tokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);

        // The shell row. google_id is replaced so the same Google account can sign up fresh later.
        user.setGoogleId("deleted-" + userId);
        user.setEmail(null);
        user.setFirstName("Deleted");
        user.setLastName("user");
        user.setMobileNumber(null);
        user.setAddressLine1(null);
        user.setAddressLine2(null);
        user.setCity(null);
        user.setState(null);
        user.setPostalCode(null);
        user.setDeletedAt(Instant.now());
        user.setDeletionRequestedAt(null);
        userRepository.save(user);

        log.info("Account deleted: user id {}", userId);
        return new StoredFiles(publicUrls, privateKeys);
    }

    private void scrubVendorProfile(VendorProfile profile, List<String> publicUrls, List<String> privateKeys) {
        Long profileId = profile.getId();

        List<VendorGalleryPhoto> photos = galleryPhotoRepository.findByVendorProfileIdOrderByCreatedAtDesc(profileId);
        photos.forEach(photo -> publicUrls.add(photo.getImageUrl()));
        galleryPhotoRepository.deleteAll(photos);

        List<VendorPackageImage> packageImages =
                packageImageRepository.findByVendorPackage_VendorProfile_IdOrderByCreatedAtDesc(profileId);
        packageImages.forEach(image -> publicUrls.add(image.getImageUrl()));
        packageImageRepository.deleteAll(packageImages);

        List<VendorLegalDocument> documents = legalDocumentRepository.findByVendorProfileIdOrderByCreatedAtDesc(profileId);
        documents.forEach(document -> privateKeys.add(document.getFileKey()));
        legalDocumentRepository.deleteAll(documents);

        List<VendorPaymentMethod> paymentMethods = paymentMethodRepository.findByVendorProfileIdOrderByCreatedAtDesc(profileId);
        paymentMethods.forEach(method -> publicUrls.add(method.getQrImageUrl()));
        paymentMethodRepository.deleteAll(paymentMethods);

        socialMediaLinkRepository.deleteAll(socialMediaLinkRepository.findByVendorProfileIdOrderByCreatedAtAsc(profileId));
        googleCalendarConnectionRepository.deleteByVendorProfileId(profileId);

        // Packages stay in the database because planners' existing quotations and bookings refer to them
        // (name, price), but they are hidden from the storefront.
        List<VendorPackage> packages = vendorPackageRepository.findByVendorProfileIdOrderByCreatedAtDesc(profileId);
        packages.forEach(pkg -> pkg.setActive(false));
        vendorPackageRepository.saveAll(packages);

        // A deleted supplier disappears from planners' bookmarks and event suggestions; the suggestion rows
        // keep their category and budget as category-only suggestions.
        eventBookmarkedVendorRepository.deleteByVendorProfileId(profileId);
        eventSuggestedVendorRepository.clearVendorProfile(profileId);

        publicUrls.add(profile.getLogoImageUrl());
        privateKeys.add(profile.getIdCardKey());
        privateKeys.add(profile.getSelfieKey());

        // Make the storefront disappear from search and browsing, and drop personal and business details.
        profile.setSlug("deleted-" + profileId);
        profile.setReferralCode(null);
        profile.setBusinessName("Deleted supplier");
        profile.setOwnerName(null);
        profile.setDescription(null);
        profile.setStorefrontOverview(null);
        profile.setLogoImageUrl(null);
        profile.setContactEmail(null);
        profile.setPhoneNumber(null);
        profile.setFacebookPageUrl(null);
        profile.setAddressLine1(null);
        profile.setAddressLine2(null);
        profile.setCity(null);
        profile.setState(null);
        profile.setPostalCode(null);
        profile.setIdCardKey(null);
        profile.setSelfieKey(null);
        profile.setPaymentInstructions(null);
        profile.setVerified(false);
        profile.setTopVendor(false);
        profile.getBusinessTypes().clear();
        profile.getOperatingAreas().clear();
        profile.getCateredEventTypes().clear();
        vendorProfileRepository.save(profile);
    }

    private void deleteFiles(StoredFiles files) {
        if (files == null) {
            return;
        }
        files.publicUrls().forEach(s3UploadService::deletePublicByUrlQuietly);
        files.privateKeys().forEach(key -> s3UploadService.deleteQuietly(key, Visibility.PRIVATE));
    }

    /** Files to remove from storage once the database scrub has committed. */
    record StoredFiles(List<String> publicUrls, List<String> privateKeys) {}
}
