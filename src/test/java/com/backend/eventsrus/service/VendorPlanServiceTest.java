package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.BookingStatusEventRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VendorPlanServiceTest {

    private static final long USER_ID = 7L;

    @Mock
    private VendorSubscriptionRepository vendorSubscriptionRepository;
    @Mock
    private SystemSettingService systemSettingService;
    @Mock
    private BookingStatusEventRepository bookingStatusEventRepository;

    private VendorPlanService service;

    @BeforeEach
    void setUp() {
        service = new VendorPlanService(vendorSubscriptionRepository, systemSettingService, bookingStatusEventRepository);
        lenient().when(systemSettingService.getInt(SystemSettingKey.SUBSCRIPTION_GRACE_PERIOD_DAYS)).thenReturn(3);
        lenient().when(systemSettingService.getInt(SystemSettingKey.SUBSCRIPTION_WARNING_DAYS)).thenReturn(7);
    }

    private void latest(Instant end, int carryOverDays) {
        VendorSubscription row = VendorSubscription.builder()
                .plan(PlanTier.PRO).billingSource(BillingSource.GOOGLE_PLAY).status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(Instant.now().minus(1, ChronoUnit.DAYS)).currentPeriodEnd(end)
                .carryOverDays(carryOverDays).build();
        when(vendorSubscriptionRepository.findFirstByUserIdOrderByCurrentPeriodStartDesc(USER_ID))
                .thenReturn(Optional.of(row));
    }

    @Test
    void carriedOverDaysAreAddedOnTopOfThePlansOwnEnd() {
        Instant end = Instant.now().plus(90, ChronoUnit.DAYS);
        latest(end, 40);

        assertThat(service.getEffectivePlan(USER_ID).expiresAt()).isEqualTo(end.plus(40, ChronoUnit.DAYS));
    }

    @Test
    void carriedOverDaysKeepAnAlreadyEndedPlanAliveUntilTheyRunOut() {
        latest(Instant.now().minus(5, ChronoUnit.DAYS), 10);

        VendorPlanService.EffectivePlan plan = service.getEffectivePlan(USER_ID);

        assertThat(plan.plan()).isEqualTo(PlanTier.PRO);
        assertThat(plan.expired()).isFalse();
        assertThat(plan.inGracePeriod()).isFalse();
    }

    @Test
    void aPlanThatEndsRightNowIsInTheGracePeriodForThirtyDaysLikeANewSuppliersTrial() {
        when(systemSettingService.getInt(SystemSettingKey.SUBSCRIPTION_GRACE_PERIOD_DAYS)).thenReturn(30);
        latest(Instant.now().minusSeconds(5), 0);

        VendorPlanService.EffectivePlan plan = service.getEffectivePlan(USER_ID);

        assertThat(plan.plan()).isEqualTo(PlanTier.PRO);
        assertThat(plan.inGracePeriod()).isTrue();
        assertThat(plan.expired()).isFalse();
        assertThat(plan.graceEndsAt()).isAfter(Instant.now().plus(29, ChronoUnit.DAYS));
    }

    @Test
    void remainingDaysRoundsUpAndIncludesCarryOver() {
        latest(Instant.now().plus(10, ChronoUnit.DAYS).plusSeconds(60), 5);

        assertThat(service.remainingDays(USER_ID)).isEqualTo(16);
    }

    @Test
    void remainingDaysIsZeroWhenNoPlanOrEnded() {
        when(vendorSubscriptionRepository.findFirstByUserIdOrderByCurrentPeriodStartDesc(USER_ID))
                .thenReturn(Optional.empty());
        assertThat(service.remainingDays(USER_ID)).isZero();

        latest(Instant.now().minus(30, ChronoUnit.DAYS), 0);
        assertThat(service.remainingDays(USER_ID)).isZero();
    }

    // ---- free until N counted bookings (promo code FREE3BOOKINGS) ----

    private void goalSubscription(Instant deadline, int goal) {
        User user = new User();
        user.setId(USER_ID);
        VendorSubscription row = VendorSubscription.builder()
                .user(user).plan(PlanTier.PRO).billingSource(BillingSource.FREE_GRANT).status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(Instant.now().minus(10, ChronoUnit.DAYS)).currentPeriodEnd(deadline)
                .bookingsGoal(goal).bookingsGoalDeadline(deadline).build();
        when(vendorSubscriptionRepository.findFirstByUserIdOrderByCurrentPeriodStartDesc(USER_ID))
                .thenReturn(Optional.of(row));
    }

    @Test
    void aFreeUntilBookingsSupplierBelowTheGoalStaysLiveUntilTheDeadline() {
        Instant deadline = Instant.now().plus(300, ChronoUnit.DAYS);
        goalSubscription(deadline, 3);
        when(bookingStatusEventRepository.countedBookingTimes(USER_ID, 3))
                .thenReturn(List.<Object>of(Instant.now().minus(5, ChronoUnit.DAYS), Instant.now().minus(2, ChronoUnit.DAYS)));

        VendorPlanService.EffectivePlan plan = service.getEffectivePlan(USER_ID);

        assertThat(plan.plan()).isEqualTo(PlanTier.PRO);
        assertThat(plan.expired()).isFalse();
        assertThat(plan.inGracePeriod()).isFalse();
        assertThat(plan.expiresAt()).isEqualTo(deadline);
    }

    @Test
    void reachingTheGoalEndsTheFreePeriodAtTheMomentOfTheLastBookingThenTheGracePeriodStarts() {
        goalSubscription(Instant.now().plus(300, ChronoUnit.DAYS), 3);
        Instant third = Instant.now().minus(1, ChronoUnit.DAYS);
        when(bookingStatusEventRepository.countedBookingTimes(USER_ID, 3))
                .thenReturn(List.<Object>of(Instant.now().minus(9, ChronoUnit.DAYS), Instant.now().minus(4, ChronoUnit.DAYS), third));

        VendorPlanService.EffectivePlan plan = service.getEffectivePlan(USER_ID);

        assertThat(plan.expiresAt()).isEqualTo(third);
        assertThat(plan.inGracePeriod()).isTrue();
        assertThat(plan.expired()).isFalse();
    }

    @Test
    void afterTheGoalAndTheGracePeriodTheSupplierIsExpiredAndSeesThePaywall() {
        goalSubscription(Instant.now().plus(300, ChronoUnit.DAYS), 3);
        when(bookingStatusEventRepository.countedBookingTimes(USER_ID, 3)).thenReturn(List.<Object>of(
                Instant.now().minus(60, ChronoUnit.DAYS), Instant.now().minus(50, ChronoUnit.DAYS),
                Instant.now().minus(40, ChronoUnit.DAYS)));

        VendorPlanService.EffectivePlan plan = service.getEffectivePlan(USER_ID);

        assertThat(plan.plan()).isNull();
        assertThat(plan.expired()).isTrue();
    }

    @Test
    void theDeadlineEndsTheFreePeriodEvenWithFewBookings() {
        goalSubscription(Instant.now().minus(40, ChronoUnit.DAYS), 3);
        when(bookingStatusEventRepository.countedBookingTimes(USER_ID, 3))
                .thenReturn(List.<Object>of(Instant.now().minus(60, ChronoUnit.DAYS)));

        VendorPlanService.EffectivePlan plan = service.getEffectivePlan(USER_ID);

        assertThat(plan.plan()).isNull();
        assertThat(plan.expired()).isTrue();
    }

    @Test
    void aFreeUntilBookingsSupplierGetsTheSameSharedGracePeriodAsEveryoneElse() {
        // The shared grace setting is 3 days (setUp).
        goalSubscription(Instant.now().plus(300, ChronoUnit.DAYS), 3);
        when(bookingStatusEventRepository.countedBookingTimes(USER_ID, 3)).thenReturn(List.<Object>of(
                Instant.now().minus(12, ChronoUnit.DAYS), Instant.now().minus(11, ChronoUnit.DAYS),
                Instant.now().minus(2, ChronoUnit.DAYS)));
        assertThat(service.getEffectivePlan(USER_ID).inGracePeriod()).isTrue();

        when(bookingStatusEventRepository.countedBookingTimes(USER_ID, 3)).thenReturn(List.<Object>of(
                Instant.now().minus(12, ChronoUnit.DAYS), Instant.now().minus(11, ChronoUnit.DAYS),
                Instant.now().minus(5, ChronoUnit.DAYS)));
        assertThat(service.getEffectivePlan(USER_ID).expired()).isTrue();
    }

    @Test
    void payingEarlyDuringAFreeUntilBookingsGrantDoesNotCarryOverTheFreeDays() {
        goalSubscription(Instant.now().plus(300, ChronoUnit.DAYS), 3);

        assertThat(service.remainingDays(USER_ID)).isZero();
    }

    @Test
    void aNormalDateBasedPlanNeverLooksAtBookings() {
        latest(Instant.now().plus(30, ChronoUnit.DAYS), 0);

        service.getEffectivePlan(USER_ID);

        verifyNoInteractions(bookingStatusEventRepository);
    }
}
