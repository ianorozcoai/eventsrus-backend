package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.exception.AccountDeletionBlockedException;
import com.backend.eventsrus.model.RefreshToken;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorGalleryPhoto;
import com.backend.eventsrus.model.VendorLegalDocument;
import com.backend.eventsrus.model.VendorPackage;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VendorProfileRepository vendorProfileRepository;
    @Mock private VendorSubscriptionRepository vendorSubscriptionRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private DeviceTokenRepository deviceTokenRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private VendorReviewRepository vendorReviewRepository;
    @Mock private EventRepository eventRepository;
    @Mock private GoogleCalendarConnectionRepository googleCalendarConnectionRepository;
    @Mock private VendorGalleryPhotoRepository galleryPhotoRepository;
    @Mock private VendorPackageImageRepository packageImageRepository;
    @Mock private VendorLegalDocumentRepository legalDocumentRepository;
    @Mock private VendorPaymentMethodRepository paymentMethodRepository;
    @Mock private VendorSocialMediaLinkRepository socialMediaLinkRepository;
    @Mock private VendorPackageRepository vendorPackageRepository;
    @Mock private EventSuggestedVendorRepository eventSuggestedVendorRepository;
    @Mock private EventBookmarkedVendorRepository eventBookmarkedVendorRepository;
    @Mock private S3UploadService s3UploadService;
    @Mock private PlatformTransactionManager transactionManager;

    private AccountDeletionService service;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionService(
                userRepository, vendorProfileRepository, vendorSubscriptionRepository, bookingRepository,
                refreshTokenRepository, deviceTokenRepository, notificationRepository,
                vendorReviewRepository, eventRepository,
                googleCalendarConnectionRepository, galleryPhotoRepository, packageImageRepository,
                legalDocumentRepository, paymentMethodRepository, socialMediaLinkRepository,
                vendorPackageRepository, eventSuggestedVendorRepository, eventBookmarkedVendorRepository,
                s3UploadService, transactionManager);
    }

    private static User planner() {
        return User.builder().id(7L).email("planner@example.com").googleId("g-7").role(Role.PLANNER).build();
    }

    @Nested
    class AdminList {

        @Test
        void pendingRequestsShowWhoAndWhenTheyWillBeScrubbed() {
            Instant requested = Instant.now().minus(Duration.ofDays(3));
            User user = User.builder().id(7L).email("planner@example.com").firstName("Ana").lastName("Cruz")
                    .role(Role.PLANNER).build();
            user.setDeletionRequestedAt(requested);
            when(userRepository.findByDeletionRequestedAtIsNotNullAndDeletedAtIsNullOrderByDeletionRequestedAtDesc())
                    .thenReturn(List.of(user));
            when(userRepository.findTop200ByDeletedAtIsNotNullOrderByDeletedAtDesc()).thenReturn(List.of());

            var rows = service.listForAdmin();

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).status()).isEqualTo("PENDING");
            assertThat(rows.get(0).name()).isEqualTo("Ana Cruz");
            assertThat(rows.get(0).email()).isEqualTo("planner@example.com");
            assertThat(rows.get(0).scheduledDeletionAt()).isEqualTo(requested.plus(Duration.ofDays(30)));
            assertThat(rows.get(0).deletedAt()).isNull();
        }

        @Test
        void completedDeletionsNoLongerExposeNameOrEmail() {
            Instant deleted = Instant.now().minus(Duration.ofDays(1));
            User user = User.builder().id(8L).email("scrubbed-8@deleted.invalid").role(Role.VENDOR).build();
            user.setDeletionRequestedAt(Instant.now().minus(Duration.ofDays(31)));
            user.setDeletedAt(deleted);
            when(userRepository.findByDeletionRequestedAtIsNotNullAndDeletedAtIsNullOrderByDeletionRequestedAtDesc())
                    .thenReturn(List.of());
            when(userRepository.findTop200ByDeletedAtIsNotNullOrderByDeletedAtDesc()).thenReturn(List.of(user));

            var rows = service.listForAdmin();

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).status()).isEqualTo("COMPLETED");
            assertThat(rows.get(0).name()).isNull();
            assertThat(rows.get(0).email()).isNull();
            assertThat(rows.get(0).deletedAt()).isEqualTo(deleted);
            assertThat(rows.get(0).role()).isEqualTo(Role.VENDOR);
        }
    }

    @Nested
    class RequestDeletion {

        @Test
        void schedulesDeletionThirtyDaysOutAndEndsSessions() {
            User user = planner();
            RefreshToken token = new RefreshToken();
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(user));
            when(vendorSubscriptionRepository.existsByUserIdAndStatusIn(eq(7L), anyCollection())).thenReturn(false);
            when(bookingRepository.countLiveUpcomingBookingsFor(eq(7L), anyCollection(), any())).thenReturn(0L);
            when(refreshTokenRepository.findByUserIdAndRevokedFalse(7L)).thenReturn(List.of(token));

            Instant scheduledFor = service.requestDeletion("planner@example.com");

            assertThat(user.getDeletionRequestedAt()).isNotNull();
            assertThat(scheduledFor).isEqualTo(user.getDeletionRequestedAt().plus(Duration.ofDays(30)));
            assertThat(token.isRevoked()).isTrue();
            verify(deviceTokenRepository).deleteByUserId(7L);
            verify(userRepository).save(user);
        }

        @Test
        void isBlockedByAnActiveSubscription() {
            User user = planner();
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(user));
            when(vendorSubscriptionRepository.existsByUserIdAndStatusIn(eq(7L), anyCollection())).thenReturn(true);

            assertThatThrownBy(() -> service.requestDeletion("planner@example.com"))
                    .isInstanceOf(AccountDeletionBlockedException.class)
                    .hasMessageContaining("cancel");
            assertThat(user.getDeletionRequestedAt()).isNull();
            verify(userRepository, never()).save(any());
        }

        @Test
        void isBlockedByAnUpcomingBooking() {
            User user = planner();
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(user));
            when(vendorSubscriptionRepository.existsByUserIdAndStatusIn(eq(7L), anyCollection())).thenReturn(false);
            when(bookingRepository.countLiveUpcomingBookingsFor(eq(7L), anyCollection(), any())).thenReturn(2L);

            assertThatThrownBy(() -> service.requestDeletion("planner@example.com"))
                    .isInstanceOf(AccountDeletionBlockedException.class)
                    .hasMessageContaining("booking");
            verify(userRepository, never()).save(any());
        }

        @Test
        void adminAccountsCannotBeDeleted() {
            User admin = User.builder().id(1L).email("a@example.com").googleId("g-1").role(Role.ADMIN).build();
            when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(admin));

            assertThatThrownBy(() -> service.requestDeletion("a@example.com"))
                    .isInstanceOf(AccountDeletionBlockedException.class);
        }
    }

    @Nested
    class Purge {

        @Test
        void scrubsThePersonAndKeepsTheRowAsAnAnonymousShell() {
            User user = planner();
            user.setFirstName("Ana");
            user.setLastName("Cruz");
            user.setMobileNumber("+639171234567");
            user.setCity("Manila");
            user.setDeletionRequestedAt(Instant.now().minus(Duration.ofDays(31)));
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(vendorProfileRepository.findByUserId(7L)).thenReturn(Optional.empty());
            when(refreshTokenRepository.findByUserIdAndRevokedFalse(7L)).thenReturn(List.of());

            AccountDeletionService.StoredFiles files = service.purge(7L);

            assertThat(user.getEmail()).isNull();
            assertThat(user.getMobileNumber()).isNull();
            assertThat(user.getCity()).isNull();
            assertThat(user.getFirstName()).isEqualTo("Deleted");
            assertThat(user.getGoogleId()).isEqualTo("deleted-7");
            assertThat(user.getDeletedAt()).isNotNull();
            assertThat(user.getDeletionRequestedAt()).isNull();
            // Chat messages are deliberately kept: nothing in the scrub touches them.
            verify(vendorReviewRepository).scrubCommentsWrittenBy(7L);
            verify(eventRepository).scrubEventsOf(7L);
            verify(notificationRepository).deleteByRecipientId(7L);
            verify(deviceTokenRepository).deleteByUserId(7L);
        }

        @Test
        void removesASuppliersStorefrontContentAndCollectsItsFiles() {
            User user = User.builder().id(7L).email("v@example.com").googleId("g-7").role(Role.VENDOR).build();
            VendorProfile profile = VendorProfile.builder()
                    .id(9L).user(user).slug("sparkle-events").businessName("Sparkle Events")
                    .idCardKey("id-key").selfieKey("selfie-key")
                    .logoImageUrl("https://cdn.example/logo.png").build();
            VendorGalleryPhoto photo = new VendorGalleryPhoto();
            photo.setImageUrl("https://cdn.example/photo.png");
            VendorLegalDocument document = new VendorLegalDocument();
            document.setFileKey("dti-key");

            VendorPackage activePackage = new VendorPackage();
            activePackage.setActive(true);

            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(vendorProfileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
            when(vendorPackageRepository.findByVendorProfileIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(activePackage));
            when(galleryPhotoRepository.findByVendorProfileIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(photo));
            when(packageImageRepository.findByVendorPackage_VendorProfile_IdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
            when(legalDocumentRepository.findByVendorProfileIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(document));
            when(paymentMethodRepository.findByVendorProfileIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
            when(socialMediaLinkRepository.findByVendorProfileIdOrderByCreatedAtAsc(9L)).thenReturn(List.of());
            when(refreshTokenRepository.findByUserIdAndRevokedFalse(7L)).thenReturn(List.of());

            AccountDeletionService.StoredFiles files = service.purge(7L);

            assertThat(profile.getSlug()).isEqualTo("deleted-9");
            assertThat(profile.getBusinessName()).isEqualTo("Deleted supplier");
            assertThat(profile.getIdCardKey()).isNull();
            assertThat(profile.getSelfieKey()).isNull();
            assertThat(profile.getLogoImageUrl()).isNull();
            assertThat(profile.isVerified()).isFalse();
            assertThat(files.publicUrls()).contains("https://cdn.example/photo.png", "https://cdn.example/logo.png");
            assertThat(files.privateKeys()).contains("dti-key", "id-key", "selfie-key");
            verify(galleryPhotoRepository).deleteAll(List.of(photo));
            verify(legalDocumentRepository).deleteAll(List.of(document));
            verify(googleCalendarConnectionRepository).deleteByVendorProfileId(9L);

            // Planners' existing quotations and bookings still refer to the packages, so they are kept
            // but hidden; planners' bookmarks and suggestions stop pointing at the deleted supplier.
            assertThat(activePackage.isActive()).isFalse();
            verify(vendorPackageRepository).saveAll(List.of(activePackage));
            verify(eventBookmarkedVendorRepository).deleteByVendorProfileId(9L);
            verify(eventSuggestedVendorRepository).clearVendorProfile(9L);
        }

        @Test
        void doesNothingForAnAccountThatIsAlreadyDeleted() {
            User user = planner();
            user.setDeletedAt(Instant.now());
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));

            AccountDeletionService.StoredFiles files = service.purge(7L);

            assertThat(files.publicUrls()).isEmpty();
            verify(userRepository, never()).save(any());
        }
    }
}
