package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.model.SubscriptionReminder;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.SubscriptionReminderRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionReminderServiceTest {

    private static final long USER_ID = 5L;

    @Mock
    private VendorSubscriptionRepository vendorSubscriptionRepository;
    @Mock
    private SubscriptionReminderRepository subscriptionReminderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VendorPlanService vendorPlanService;
    @Mock
    private SmsService smsService;

    private SubscriptionReminderService service;
    private User vendor;

    @BeforeEach
    void setUp() {
        service = new SubscriptionReminderService(
                vendorSubscriptionRepository, subscriptionReminderRepository, userRepository,
                vendorPlanService, smsService);
        vendor = User.builder().id(USER_ID).role(Role.VENDOR).mobileNumber("09171234567").build();
        lenient().when(vendorSubscriptionRepository.findUserIdsWithPeriodEndAfter(any())).thenReturn(List.of(USER_ID));
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(vendor));
    }

    private void plan(BillingSource source, SubscriptionStatus status, boolean inGrace, Instant end) {
        Instant expiresAt = inGrace ? Instant.now().minus(1, ChronoUnit.DAYS) : end;
        Instant graceEnd = inGrace ? end : null;
        when(vendorPlanService.getEffectivePlan(USER_ID)).thenReturn(
                new VendorPlanService.EffectivePlan(PlanTier.PRO, expiresAt, false, false, inGrace, graceEnd, source));
        when(vendorSubscriptionRepository.findFirstByUserIdOrderByCurrentPeriodStartDesc(USER_ID)).thenReturn(Optional.of(
                VendorSubscription.builder().user(vendor).plan(PlanTier.PRO).billingSource(source).status(status).build()));
    }

    private static Instant inDays(double days) {
        return Instant.now().plusSeconds((long) (days * 86_400));
    }

    @Test
    void bucketsAreFourteenSevenAndThreeDaysAndNothingInBetween() {
        assertThat(SubscriptionReminderService.bucketFor(30)).isNull();
        assertThat(SubscriptionReminderService.bucketFor(15)).isNull();
        assertThat(SubscriptionReminderService.bucketFor(14)).isEqualTo(14);
        assertThat(SubscriptionReminderService.bucketFor(8)).isEqualTo(14);
        assertThat(SubscriptionReminderService.bucketFor(7)).isEqualTo(7);
        assertThat(SubscriptionReminderService.bucketFor(4)).isEqualTo(7);
        assertThat(SubscriptionReminderService.bucketFor(3)).isEqualTo(3);
        assertThat(SubscriptionReminderService.bucketFor(1)).isEqualTo(3);
        assertThat(SubscriptionReminderService.bucketFor(0)).isNull();
    }

    @Test
    void aTrialSupplierGetsTheFourteenDayTextOnceAndItIsRecorded() {
        plan(BillingSource.FREE_GRANT, SubscriptionStatus.ACTIVE, true, inDays(13.5));
        when(subscriptionReminderRepository.existsByUserIdAndAccessEndDateAndLeadDays(anyLong(), any(), anyInt()))
                .thenReturn(false);
        when(smsService.sendSubscriptionReminder(any(), any(), any())).thenReturn(true);

        assertThat(service.runOnce()).isEqualTo(1);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(smsService).sendSubscriptionReminder(any(), message.capture(), any());
        assertThat(message.getValue()).contains("free trial ends in 14 days");
        ArgumentCaptor<SubscriptionReminder> saved = ArgumentCaptor.forClass(SubscriptionReminder.class);
        verify(subscriptionReminderRepository).save(saved.capture());
        assertThat(saved.getValue().getLeadDays()).isEqualTo(14);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void theSameReminderIsNeverSentTwice() {
        plan(BillingSource.GCASH, SubscriptionStatus.ACTIVE, false, inDays(6.5));
        when(subscriptionReminderRepository.existsByUserIdAndAccessEndDateAndLeadDays(anyLong(), any(), anyInt()))
                .thenReturn(true);

        assertThat(service.runOnce()).isZero();

        verify(smsService, never()).sendSubscriptionReminder(any(), any(), any());
    }

    @Test
    void aReminderIsNotRecordedWhenTheSmsDidNotGoOut() {
        plan(BillingSource.GCASH, SubscriptionStatus.ACTIVE, false, inDays(2.5));
        when(subscriptionReminderRepository.existsByUserIdAndAccessEndDateAndLeadDays(anyLong(), any(), anyInt()))
                .thenReturn(false);
        when(smsService.sendSubscriptionReminder(any(), any(), any())).thenReturn(false);

        assertThat(service.runOnce()).isZero();

        verify(subscriptionReminderRepository, never()).save(any());
    }

    @Test
    void noTextBetweenReminderDays() {
        plan(BillingSource.FREE_GRANT, SubscriptionStatus.ACTIVE, true, inDays(20));

        assertThat(service.runOnce()).isZero();

        verify(smsService, never()).sendSubscriptionReminder(any(), any(), any());
    }

    @Test
    void suppliersWhosePlanRenewsByItselfAreNotReminded() {
        plan(BillingSource.GOOGLE_PLAY, SubscriptionStatus.ACTIVE, false, inDays(2));

        assertThat(service.runOnce()).isZero();

        verify(smsService, never()).sendSubscriptionReminder(any(), any(), any());
    }

    @Test
    void aCancelledGooglePlayPlanIsRemindedBecauseItWillNotRenew() {
        plan(BillingSource.GOOGLE_PLAY, SubscriptionStatus.CANCELLED, false, inDays(2));
        when(subscriptionReminderRepository.existsByUserIdAndAccessEndDateAndLeadDays(anyLong(), any(), anyInt()))
                .thenReturn(false);
        when(smsService.sendSubscriptionReminder(any(), contains("PRO access ends in"), any())).thenReturn(true);

        assertThat(service.runOnce()).isEqualTo(1);
    }

    @Test
    void aGcashPaymentStillUnderReviewIsNotReminded() {
        plan(BillingSource.GCASH, SubscriptionStatus.PAYMENT_VERIFICATION, false, inDays(2));

        assertThat(service.runOnce()).isZero();

        verify(smsService, never()).sendSubscriptionReminder(any(), any(), any());
    }

    @Test
    void plannersAndDeletedAccountsAreNeverTexted() {
        vendor.setRole(Role.PLANNER);
        assertThat(service.runOnce()).isZero();

        vendor.setRole(Role.VENDOR);
        vendor.setDeletionRequestedAt(Instant.now());
        assertThat(service.runOnce()).isZero();

        verify(smsService, never()).sendSubscriptionReminder(any(), any(), any());
    }

    @Test
    void theMessageNamesTheEndDateAndSingularDay() {
        String message = SubscriptionReminderService.messageFor(false, 1, LocalDate.of(2026, 11, 25));

        assertThat(message).isEqualTo(
                "EventsRUs: your PRO access ends in 1 day (Nov 25, 2026). Renew in the app to keep your supplier tools.");
    }
}
