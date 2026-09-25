package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.dto.VendorOnboardingRequest;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SignupIntent;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.AccountIdentityConflictException;
import com.backend.eventsrus.exception.InvalidPromoCodeException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Each Google account is locked to exactly one identity (planner or
 * vendor), decided by whichever login door it first walked through and
 * recorded permanently as User#signupIntent - separate from role, which
 * still freely flows PLANNER -> VENDOR at becomeVendor time. See
 * UserService#findOrCreateFromGoogle.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private VendorSubscriptionRepository vendorSubscriptionRepository;
    @Mock
    private S3UploadService s3UploadService;
    @Mock
    private VendorLegalDocumentService vendorLegalDocumentService;
    @Mock
    private VendorBillingHistoryService vendorBillingHistoryService;
    @Mock
    private RecaptchaVerificationService recaptchaVerificationService;
    @Mock
    private VendorReferralService vendorReferralService;
    @Mock
    private PromoCodeService promoCodeService;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private SystemSettingService systemSettingService;
    @Mock
    private AdminNotificationEmailService adminNotificationEmailService;
    @Mock
    private VendorPlanService vendorPlanService;

    private UserService userService;

    private static final GoogleUserInfo GOOGLE_USER =
            new GoogleUserInfo("google-123", "person@example.com", "Ian", "Orozco");

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                vendorProfileRepository,
                vendorSubscriptionRepository,
                s3UploadService,
                vendorLegalDocumentService,
                vendorBillingHistoryService,
                recaptchaVerificationService,
                vendorReferralService,
                promoCodeService,
                bookingRepository,
                eventRepository,
                systemSettingService,
                adminNotificationEmailService,
                vendorPlanService);
    }

    @Nested
    class FindOrCreateFromGoogle {

        @Test
        void createsANewPlannerWithPlannerIntentWhenIntentIsPlanner() {
            when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            User created = userService.findOrCreateFromGoogle(GOOGLE_USER, "planner");

            assertThat(created.getRole()).isEqualTo(Role.PLANNER);
            assertThat(created.getSignupIntent()).isEqualTo(SignupIntent.PLANNER);
            verify(adminNotificationEmailService).notifyNewPlanner(created);
        }

        @Test
        void createsANewUserWithVendorIntentWhenIntentIsVendor() {
            when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            User created = userService.findOrCreateFromGoogle(GOOGLE_USER, "vendor");

            // role starts PLANNER either way - becomeVendor is what flips it -
            // but the identity lock is already VENDOR from this first login.
            assertThat(created.getRole()).isEqualTo(Role.PLANNER);
            assertThat(created.getSignupIntent()).isEqualTo(SignupIntent.VENDOR);
            // No planner alert for a vendor-intent signup - it gets its own
            // alert later, once onboarding actually finishes (becomeVendor).
            verify(adminNotificationEmailService, never()).notifyNewPlanner(any());
        }

        @Test
        void defaultsToPlannerIntentWhenIntentIsBlankOrUnrecognized() {
            when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            User created = userService.findOrCreateFromGoogle(GOOGLE_USER, null);

            assertThat(created.getSignupIntent()).isEqualTo(SignupIntent.PLANNER);
        }

        @Test
        void allowsAReturningPlannerLoggingInWithMatchingIntent() {
            User existing = User.builder().googleId("google-123").role(Role.PLANNER)
                    .signupIntent(SignupIntent.PLANNER).build();
            when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(userService.findOrCreateFromGoogle(GOOGLE_USER, "planner")).isSameAs(existing);
            // A returning login is not a new signup - never re-alert.
            verify(adminNotificationEmailService, never()).notifyNewPlanner(any());
        }

        @Test
        void allowsResumingOnesOwnIncompleteVendorOnboarding() {
            // signupIntent is already VENDOR from their first vendor-intent
            // login, even though role hasn't flipped yet because they never
            // finished the onboarding form - this must be allowed, not
            // treated as a conflict.
            User existing = User.builder().googleId("google-123").role(Role.PLANNER)
                    .signupIntent(SignupIntent.VENDOR).build();
            when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(userService.findOrCreateFromGoogle(GOOGLE_USER, "vendor")).isSameAs(existing);
        }

        @Test
        void allowsAReturningFullVendorLoggingInWithVendorIntent() {
            User existing = User.builder().googleId("google-123").role(Role.VENDOR)
                    .signupIntent(SignupIntent.VENDOR).build();
            when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(userService.findOrCreateFromGoogle(GOOGLE_USER, "vendor")).isSameAs(existing);
        }

        @Test
        void updatesLastLoginAtOnEveryLogin() {
            User existing = User.builder().googleId("google-123").role(Role.VENDOR)
                    .signupIntent(SignupIntent.VENDOR).build();
            when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            userService.findOrCreateFromGoogle(GOOGLE_USER, "vendor");

            assertThat(existing.getLastLoginAt()).isNotNull();
            verify(userRepository).save(existing);
        }

        @Test
        void rejectsAnEstablishedPlannerTryingToSignUpAsVendor() {
            User existing = User.builder().googleId("google-123").role(Role.PLANNER)
                    .signupIntent(SignupIntent.PLANNER).build();
            when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> userService.findOrCreateFromGoogle(GOOGLE_USER, "vendor"))
                    .isInstanceOf(AccountIdentityConflictException.class)
                    .hasMessageContaining("already registered as a planner account");
        }

        @Test
        void rejectsAVendorTryingToLogInAsPlanner() {
            User existing = User.builder().googleId("google-123").role(Role.VENDOR)
                    .signupIntent(SignupIntent.VENDOR).build();
            when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> userService.findOrCreateFromGoogle(GOOGLE_USER, "planner"))
                    .isInstanceOf(AccountIdentityConflictException.class)
                    .hasMessageContaining("already registered as a vendor account");
        }

        @Test
        void skipsTheCheckEntirelyWhenAReturningLoginDoesNotDeclareAnIntent() {
            // The Flutter app doesn't send intent yet - must not be blocked.
            User existing = User.builder().googleId("google-123").role(Role.VENDOR)
                    .signupIntent(SignupIntent.VENDOR).build();
            when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(userService.findOrCreateFromGoogle(GOOGLE_USER, null)).isSameAs(existing);
        }
    }

    @Nested
    class BecomeVendor {

        private User plannerUser() {
            return User.builder().id(1L).email("vendor@example.com").role(Role.PLANNER)
                    .signupIntent(SignupIntent.VENDOR).build();
        }

        private VendorOnboardingRequest requestWithPromoCode(String promoCode) {
            VendorOnboardingRequest request = new VendorOnboardingRequest();
            request.setBusinessName("Test Business");
            request.setOwnerName("Test Owner");
            request.setContactEmail("owner@example.com");
            request.setPhoneNumber("09171234567");
            request.setAcceptedTerms(true);
            request.setPromoCode(promoCode);
            return request;
        }

        private UserService.VendorUploadFiles noFiles() {
            return new UserService.VendorUploadFiles(null, null, null, null, null, null);
        }

        @Test
        void grantsFreeTrialWhenPromoCodeIsValid() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(plannerUser()));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
            when(promoCodeService.isValidIfPresent("FREE3M")).thenReturn(true);
            when(vendorSubscriptionRepository.existsByUserId(1L)).thenReturn(false);
            when(systemSettingService.getInt(SystemSettingKey.VENDOR_TRIAL_DAYS)).thenReturn(180);

            userService.becomeVendor("vendor@example.com", requestWithPromoCode("FREE3M"), noFiles());

            ArgumentCaptor<VendorSubscription> captor = ArgumentCaptor.forClass(VendorSubscription.class);
            verify(vendorSubscriptionRepository).save(captor.capture());
            assertThat(captor.getValue().getBillingSource()).isEqualTo(BillingSource.FREE_GRANT);
            assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            verify(vendorBillingHistoryService).recordFreeGrant(any(), any(), any(), eq(BillingSource.FREE_GRANT));
        }

        @Test
        void doesNotGrantSubscriptionWhenNoPromoCodeGiven() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(plannerUser()));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
            when(promoCodeService.isValidIfPresent(null)).thenReturn(false);

            userService.becomeVendor("vendor@example.com", requestWithPromoCode(null), noFiles());

            verify(vendorSubscriptionRepository, never()).save(any());
            verify(vendorBillingHistoryService, never()).recordFreeGrant(any(), any(), any(), any());
        }

        @Test
        void rejectsInvalidPromoCodeBeforeSavingAnything() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(plannerUser()));
            when(promoCodeService.isValidIfPresent("BADCODE"))
                    .thenThrow(new InvalidPromoCodeException("That promo code isn't valid."));

            assertThatThrownBy(() ->
                    userService.becomeVendor("vendor@example.com", requestWithPromoCode("BADCODE"), noFiles()))
                    .isInstanceOf(InvalidPromoCodeException.class);

            verify(vendorProfileRepository, never()).save(any());
            verify(vendorSubscriptionRepository, never()).save(any());
        }
    }

    @Nested
    class ListPlannersForAdmin {

        @Test
        void excludesUsersMidVendorOnboarding() {
            // role=PLANNER + signupIntent=VENDOR is someone who signed up
            // through "Become a Vendor" and hasn't finished the form yet -
            // they belong in the Vendors page's "Incomplete Sign-ups" tab,
            // not double-counted here as a real planner too.
            User realPlanner = User.builder().id(1L).role(Role.PLANNER).signupIntent(SignupIntent.PLANNER).build();
            when(userRepository.findByRoleAndSignupIntentNot(Role.PLANNER, SignupIntent.VENDOR))
                    .thenReturn(java.util.List.of(realPlanner));
            when(eventRepository.countByPlannerId(1L)).thenReturn(0L);

            var result = userService.listPlannersForAdmin();

            assertThat(result).extracting(UserService.AdminPlannerListItem::id).containsExactly(1L);
            verify(userRepository, never()).findByRole(any());
        }
    }

    @Nested
    class GetPlannerForAdmin {

        @Test
        void throwsForAUserMidVendorOnboardingEvenThoughRoleIsStillPlanner() {
            User abandonedVendorSignup = User.builder().id(2L).role(Role.PLANNER).signupIntent(SignupIntent.VENDOR).build();
            when(userRepository.findById(2L)).thenReturn(Optional.of(abandonedVendorSignup));

            assertThatThrownBy(() -> userService.getPlannerForAdmin(2L))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void returnsARealPlanner() {
            User realPlanner = User.builder().id(3L).role(Role.PLANNER).signupIntent(SignupIntent.PLANNER).build();
            when(userRepository.findById(3L)).thenReturn(Optional.of(realPlanner));
            when(eventRepository.countByPlannerId(3L)).thenReturn(2L);

            assertThat(userService.getPlannerForAdmin(3L).id()).isEqualTo(3L);
        }
    }
}
