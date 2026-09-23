package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.config.PayPalProperties;
import com.backend.eventsrus.dto.SubscriptionStatusResponse;
import com.backend.eventsrus.enums.BillingCycle;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.SubscriptionConflictException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorSubscriptionEventRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * PayPal's JS SDK Smart Buttons create a subscription client-side (see
 * fragments/common.html :: proPlanPickerForm's onApprove), so
 * recordApprovedSubscription must find-or-create the local
 * VendorSubscription row rather than assume one already exists - unlike
 * confirmSubscription, which still backs the older server-initiated flow.
 */
@ExtendWith(MockitoExtension.class)
class VendorSubscriptionServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private VendorSubscriptionRepository vendorSubscriptionRepository;
    @Mock
    private VendorSubscriptionEventRepository vendorSubscriptionEventRepository;
    @Mock
    private VendorPlanService vendorPlanService;
    @Mock
    private PayPalProperties payPalProperties;
    @Mock
    private PayPalSubscriptionClient payPalSubscriptionClient;
    @Mock
    private SystemSettingService systemSettingService;
    @Mock
    private VendorBillingHistoryService vendorBillingHistoryService;
    @Mock
    private S3UploadService s3UploadService;
    @Mock
    private VendorReferralService vendorReferralService;
    @Mock
    private VendorProfileRepository vendorProfileRepository;

    private VendorSubscriptionService vendorSubscriptionService;

    private static final String EMAIL = "vendor@example.com";

    @BeforeEach
    void setUp() {
        vendorSubscriptionService = new VendorSubscriptionService(
                userRepository, vendorSubscriptionRepository, vendorSubscriptionEventRepository, vendorPlanService,
                payPalProperties, payPalSubscriptionClient, systemSettingService, vendorBillingHistoryService,
                s3UploadService, vendorReferralService, vendorProfileRepository);
    }

    private User vendorUser() {
        return User.builder().id(1L).email(EMAIL).build();
    }

    /** statusResponseFor's own dependencies - only needed by tests that reach that far. */
    private void stubStatusResponseDependencies() {
        when(vendorPlanService.getEffectivePlan(any()))
                .thenReturn(new VendorPlanService.EffectivePlan(null, null, false, false, false, null, null));
        when(systemSettingService.getInt(SystemSettingKey.VENDOR_PRO_MONTHLY_PRICE)).thenReturn(1500);
        when(payPalProperties.getPlanId()).thenReturn(new PayPalProperties.PlanId());
    }

    @Nested
    class RecordApprovedSubscription {

        @Test
        void createsANewRowWhenNoneExistsForThatPaypalSubscriptionId() {
            stubStatusResponseDependencies();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(vendorUser()));
            when(vendorSubscriptionRepository.findByPaypalSubscriptionId("I-NEW123")).thenReturn(Optional.empty());
            when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));
            when(payPalSubscriptionClient.getSubscription("I-NEW123"))
                    .thenReturn(new PayPalSubscriptionClient.PayPalSubscriptionDetails("ACTIVE", Instant.now().plusSeconds(86400)));

            vendorSubscriptionService.recordApprovedSubscription(EMAIL, "I-NEW123");

            ArgumentCaptor<VendorSubscription> captor = ArgumentCaptor.forClass(VendorSubscription.class);
            verify(vendorSubscriptionRepository).save(captor.capture());
            assertThat(captor.getValue().getPlan()).isEqualTo(PlanTier.PRO);
            assertThat(captor.getValue().getBillingSource()).isEqualTo(BillingSource.PAYPAL);
            assertThat(captor.getValue().getPaypalSubscriptionId()).isEqualTo("I-NEW123");
            assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            verify(vendorSubscriptionEventRepository).save(any());
        }

        @Test
        void reusesTheExistingRowOnARetry() {
            stubStatusResponseDependencies();
            User user = vendorUser();
            VendorSubscription existing = VendorSubscription.builder()
                    .user(user)
                    .plan(PlanTier.PRO)
                    .billingSource(BillingSource.PAYPAL)
                    .status(SubscriptionStatus.APPROVAL_PENDING)
                    .paypalSubscriptionId("I-RETRY123")
                    .build();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(vendorSubscriptionRepository.findByPaypalSubscriptionId("I-RETRY123")).thenReturn(Optional.of(existing));
            when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));
            when(payPalSubscriptionClient.getSubscription("I-RETRY123"))
                    .thenReturn(new PayPalSubscriptionClient.PayPalSubscriptionDetails("ACTIVE", Instant.now().plusSeconds(86400)));

            vendorSubscriptionService.recordApprovedSubscription(EMAIL, "I-RETRY123");

            verify(vendorSubscriptionRepository).save(existing);
            assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        void rejectsAnExistingRowThatBelongsToSomeoneElse() {
            User otherUser = User.builder().id(2L).email("someone-else@example.com").build();
            VendorSubscription existing = VendorSubscription.builder()
                    .user(otherUser)
                    .plan(PlanTier.PRO)
                    .billingSource(BillingSource.PAYPAL)
                    .status(SubscriptionStatus.APPROVAL_PENDING)
                    .paypalSubscriptionId("I-NOTMINE")
                    .build();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(vendorUser()));
            when(vendorSubscriptionRepository.findByPaypalSubscriptionId("I-NOTMINE")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> vendorSubscriptionService.recordApprovedSubscription(EMAIL, "I-NOTMINE"))
                    .isInstanceOf(IllegalStateException.class);

            verify(vendorSubscriptionRepository, never()).save(any());
        }
    }

    @Nested
    class SubmitGcashPayment {

        @Test
        void createsAPendingGcashSubscription() {
            stubStatusResponseDependencies();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(vendorUser()));
            when(vendorPlanService.getEffectivePlan(1L))
                    .thenReturn(new VendorPlanService.EffectivePlan(null, null, false, false, false, null, null));
            when(vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(1L, BillingSource.GCASH))
                    .thenReturn(Optional.empty());
            when(s3UploadService.upload(any(), any(), any()))
                    .thenReturn(new S3UploadService.UploadResult("vendors/1/subscription-payment-screenshot-abc.jpg", null));
            when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));

            MockMultipartFile screenshot = new MockMultipartFile("screenshot", "gcash.jpg", "image/jpeg", new byte[]{1, 2, 3});
            vendorSubscriptionService.submitGcashPayment(EMAIL, BillingCycle.QUARTERLY, screenshot, "Paid via GCash app");

            ArgumentCaptor<VendorSubscription> captor = ArgumentCaptor.forClass(VendorSubscription.class);
            verify(vendorSubscriptionRepository).save(captor.capture());
            assertThat(captor.getValue().getBillingSource()).isEqualTo(BillingSource.GCASH);
            assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_VERIFICATION);
            assertThat(captor.getValue().getBillingCycle()).isEqualTo(BillingCycle.QUARTERLY);
            assertThat(captor.getValue().getPaymentScreenshotKey()).isEqualTo("vendors/1/subscription-payment-screenshot-abc.jpg");
            assertThat(captor.getValue().getCurrentPeriodStart()).isNotNull();
            assertThat(captor.getValue().getCurrentPeriodEnd())
                    .isAfter(captor.getValue().getCurrentPeriodStart())
                    .isBefore(Instant.now().plusSeconds(8 * 86400));
            assertThat(captor.getValue().getVendorRemarks()).isEqualTo("Paid via GCash app");
            assertThat(captor.getValue().getRejectionReason()).isNull();
            verify(vendorBillingHistoryService).recordFreeGrant(
                    eq(captor.getValue()), any(), any(), eq(BillingSource.GCASH));
            verify(vendorSubscriptionEventRepository).save(any());
        }

        @Test
        void translatesAConcurrentDuplicateInsertIntoAFriendlyConflictError() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(vendorUser()));
            when(vendorPlanService.getEffectivePlan(1L))
                    .thenReturn(new VendorPlanService.EffectivePlan(null, null, false, false, false, null, null));
            when(vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(1L, BillingSource.GCASH))
                    .thenReturn(Optional.empty());
            when(s3UploadService.upload(any(), any(), any()))
                    .thenReturn(new S3UploadService.UploadResult("vendors/1/subscription-payment-screenshot-abc.jpg", null));
            when(vendorSubscriptionRepository.save(any(VendorSubscription.class)))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

            MockMultipartFile screenshot = new MockMultipartFile("screenshot", "gcash.jpg", "image/jpeg", new byte[]{1, 2, 3});

            assertThatThrownBy(() -> vendorSubscriptionService.submitGcashPayment(EMAIL, BillingCycle.QUARTERLY, screenshot, null))
                    .isInstanceOf(SubscriptionConflictException.class);

            verify(vendorBillingHistoryService, never()).recordFreeGrant(any(), any(), any(), any());
        }

        @Test
        void rejectsWhenAlreadyOnALivePlan() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(vendorUser()));
            when(vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(1L, BillingSource.GCASH))
                    .thenReturn(Optional.empty());
            when(vendorPlanService.getEffectivePlan(1L))
                    .thenReturn(new VendorPlanService.EffectivePlan(PlanTier.PRO, Instant.now().plusSeconds(86400), false, false, false, null, BillingSource.PAYPAL));

            MockMultipartFile screenshot = new MockMultipartFile("screenshot", "gcash.jpg", "image/jpeg", new byte[]{1, 2, 3});

            assertThatThrownBy(() -> vendorSubscriptionService.submitGcashPayment(EMAIL, BillingCycle.QUARTERLY, screenshot, null))
                    .isInstanceOf(SubscriptionConflictException.class);

            verify(vendorSubscriptionRepository, never()).save(any());
        }

        @Test
        void rejectsResubmissionWhileAPreviousSubmissionIsStillAwaitingReview() {
            VendorSubscription underReview = VendorSubscription.builder()
                    .user(vendorUser())
                    .billingSource(BillingSource.GCASH)
                    .status(SubscriptionStatus.PAYMENT_VERIFICATION)
                    .build();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(vendorUser()));
            when(vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(1L, BillingSource.GCASH))
                    .thenReturn(Optional.of(underReview));

            MockMultipartFile screenshot = new MockMultipartFile("screenshot", "gcash.jpg", "image/jpeg", new byte[]{1, 2, 3});

            assertThatThrownBy(() -> vendorSubscriptionService.submitGcashPayment(EMAIL, BillingCycle.QUARTERLY, screenshot, null))
                    .isInstanceOf(SubscriptionConflictException.class);

            verify(vendorSubscriptionRepository, never()).save(any());
        }

        @Test
        void reusesTheRejectedRowOnResubmissionAndClearsTheRejectionReason() {
            stubStatusResponseDependencies();
            VendorSubscription rejected = VendorSubscription.builder()
                    .user(vendorUser())
                    .billingSource(BillingSource.GCASH)
                    .status(SubscriptionStatus.PAYMENT_REJECTED)
                    .rejectionReason("Screenshot didn't show the reference number")
                    .build();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(vendorUser()));
            when(vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(1L, BillingSource.GCASH))
                    .thenReturn(Optional.of(rejected));
            when(s3UploadService.upload(any(), any(), any()))
                    .thenReturn(new S3UploadService.UploadResult("vendors/1/subscription-payment-screenshot-abc.jpg", null));
            when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));

            MockMultipartFile screenshot = new MockMultipartFile("screenshot", "gcash.jpg", "image/jpeg", new byte[]{1, 2, 3});
            vendorSubscriptionService.submitGcashPayment(EMAIL, BillingCycle.ANNUAL, screenshot, null);

            verify(vendorSubscriptionRepository).save(rejected);
            assertThat(rejected.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_VERIFICATION);
            assertThat(rejected.getCurrentPeriodEnd()).isNotNull();
            assertThat(rejected.getRejectionReason()).isNull();
        }
    }

    @Nested
    class VerifyGcashPayment {

        @Test
        void activatesTheSubscriptionAndConvertsAPendingReferral() {
            User user = vendorUser();
            VendorSubscription pending = VendorSubscription.builder()
                    .user(user)
                    .plan(PlanTier.PRO)
                    .billingSource(BillingSource.GCASH)
                    .billingCycle(BillingCycle.ANNUAL)
                    .status(SubscriptionStatus.PAYMENT_VERIFICATION)
                    .build();
            when(vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(1L, BillingSource.GCASH))
                    .thenReturn(Optional.of(pending));
            when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));
            when(systemSettingService.getInt(SystemSettingKey.VENDOR_PRO_MONTHLY_PRICE)).thenReturn(1500);

            vendorSubscriptionService.verifyGcashPayment(1L);

            assertThat(pending.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(pending.getCurrentPeriodStart()).isNotNull();
            assertThat(pending.getCurrentPeriodEnd()).isAfter(pending.getCurrentPeriodStart());
            assertThat(pending.getRejectionReason()).isNull();
            verify(vendorBillingHistoryService).removeFreeGrant(pending);
            verify(vendorBillingHistoryService).recordPayment(any(), any(), any(), any(), any(), any(), any(), eq(BillingSource.GCASH));
            verify(vendorReferralService).convertIfPending(1L);
            verify(vendorSubscriptionEventRepository).save(any());
        }
    }

    @Nested
    class RejectGcashPayment {

        @Test
        void marksTheRowRejectedAndRevokesTemporaryAccess() {
            VendorSubscription pending = VendorSubscription.builder()
                    .user(vendorUser())
                    .billingSource(BillingSource.GCASH)
                    .status(SubscriptionStatus.PAYMENT_VERIFICATION)
                    .currentPeriodStart(Instant.now())
                    .currentPeriodEnd(Instant.now().plusSeconds(7 * 86400))
                    .build();
            when(vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(1L, BillingSource.GCASH))
                    .thenReturn(Optional.of(pending));
            when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));

            vendorSubscriptionService.rejectGcashPayment(1L, "Screenshot doesn't show the reference number");

            verify(vendorSubscriptionRepository).save(pending);
            assertThat(pending.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_REJECTED);
            assertThat(pending.getCurrentPeriodStart()).isNull();
            assertThat(pending.getCurrentPeriodEnd()).isNull();
            assertThat(pending.getRejectionReason()).isEqualTo("Screenshot doesn't show the reference number");
            verify(vendorSubscriptionEventRepository).save(any());
        }

        @Test
        void rejectsABlankReason() {
            assertThatThrownBy(() -> vendorSubscriptionService.rejectGcashPayment(1L, "  "))
                    .isInstanceOf(com.backend.eventsrus.exception.InvalidRejectionReasonException.class);

            verify(vendorSubscriptionRepository, never()).save(any());
        }
    }

    @Nested
    class ShowWelcomePopup {

        @Test
        void trueTheFirstTimeAVendorsPlanIsGenuinelyActive() {
            stubStatusResponseDependencies();
            User user = vendorUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(vendorPlanService.getEffectivePlan(1L))
                    .thenReturn(new VendorPlanService.EffectivePlan(PlanTier.PRO, Instant.now().plusSeconds(86400), false, false, false, null, BillingSource.PAYPAL));
            when(vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(1L, BillingSource.GCASH))
                    .thenReturn(Optional.empty());

            assertThat(vendorSubscriptionService.getStatus(EMAIL).isShowWelcomePopup()).isTrue();
        }

        @Test
        void falseOnceAlreadyShown() {
            stubStatusResponseDependencies();
            User user = vendorUser();
            user.setProWelcomeShown(true);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(vendorPlanService.getEffectivePlan(1L))
                    .thenReturn(new VendorPlanService.EffectivePlan(PlanTier.PRO, Instant.now().plusSeconds(86400), false, false, false, null, BillingSource.PAYPAL));

            assertThat(vendorSubscriptionService.getStatus(EMAIL).isShowWelcomePopup()).isFalse();
        }

        @Test
        void falseWhenThereIsNoLivePlan() {
            stubStatusResponseDependencies();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(vendorUser()));

            assertThat(vendorSubscriptionService.getStatus(EMAIL).isShowWelcomePopup()).isFalse();
        }

        @Test
        void falseWhileAGcashSubmissionIsStillAwaitingReview() {
            stubStatusResponseDependencies();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(vendorUser()));
            when(vendorPlanService.getEffectivePlan(1L))
                    .thenReturn(new VendorPlanService.EffectivePlan(PlanTier.PRO, Instant.now().plusSeconds(7 * 86400), true, false, false, null, BillingSource.GCASH));
            when(vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(1L, BillingSource.GCASH))
                    .thenReturn(Optional.of(VendorSubscription.builder().status(SubscriptionStatus.PAYMENT_VERIFICATION).build()));

            assertThat(vendorSubscriptionService.getStatus(EMAIL).isShowWelcomePopup()).isFalse();
            assertThat(vendorSubscriptionService.getStatus(EMAIL).isGcashAwaitingVerification()).isTrue();
        }
    }

    @Nested
    class MarkProWelcomeShown {

        @Test
        void setsTheFlagAndSaves() {
            User user = vendorUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            vendorSubscriptionService.markProWelcomeShown(EMAIL);

            assertThat(user.isProWelcomeShown()).isTrue();
            verify(userRepository).save(user);
        }
    }

    @Nested
    class GetStatusGcashRejected {

        @Test
        void exposesTheRejectionReasonWhenTheGcashRowWasRejected() {
            stubStatusResponseDependencies();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(vendorUser()));
            when(vendorPlanService.getEffectivePlan(1L))
                    .thenReturn(new VendorPlanService.EffectivePlan(null, null, false, false, false, null, null));
            when(vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(1L, BillingSource.GCASH))
                    .thenReturn(Optional.of(VendorSubscription.builder()
                            .status(SubscriptionStatus.PAYMENT_REJECTED)
                            .rejectionReason("Amount doesn't match the selected plan")
                            .build()));

            SubscriptionStatusResponse status = vendorSubscriptionService.getStatus(EMAIL);

            assertThat(status.isGcashRejected()).isTrue();
            assertThat(status.getGcashRejectionReason()).isEqualTo("Amount doesn't match the selected plan");
            assertThat(status.isGcashAwaitingVerification()).isFalse();
        }
    }

    @Nested
    class ListGcashPayments {

        @Test
        void includesRejectionReasonVendorRemarksAndHistory() {
            User user = vendorUser();
            VendorSubscription subscription = VendorSubscription.builder()
                    .id(42L)
                    .user(user)
                    .billingSource(BillingSource.GCASH)
                    .status(SubscriptionStatus.PAYMENT_VERIFICATION)
                    .billingCycle(BillingCycle.QUARTERLY)
                    .vendorRemarks("Resubmitting with a clearer screenshot")
                    .build();
            when(vendorSubscriptionRepository.findByBillingSourceOrderByCreatedAtDesc(BillingSource.GCASH))
                    .thenReturn(java.util.List.of(subscription));
            com.backend.eventsrus.model.VendorSubscriptionEvent event = com.backend.eventsrus.model.VendorSubscriptionEvent.builder()
                    .vendorSubscription(subscription)
                    .paypalEventId("gcash-submit-42-1")
                    .eventType("GCASH_SUBMITTED")
                    .payload("Resubmitting with a clearer screenshot")
                    .occurredAt(Instant.now())
                    .build();
            when(vendorSubscriptionEventRepository.findByVendorSubscription_IdOrderByOccurredAtDesc(42L))
                    .thenReturn(java.util.List.of(event));

            var payments = vendorSubscriptionService.listGcashPayments();

            assertThat(payments).hasSize(1);
            var payment = payments.get(0);
            assertThat(payment.getVendorRemarks()).isEqualTo("Resubmitting with a clearer screenshot");
            assertThat(payment.getHistory()).hasSize(1);
            assertThat(payment.getHistory().get(0).getEventType()).isEqualTo("GCASH_SUBMITTED");
        }
    }
}
