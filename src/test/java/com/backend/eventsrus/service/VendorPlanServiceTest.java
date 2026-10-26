package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    private VendorPlanService service;

    @BeforeEach
    void setUp() {
        service = new VendorPlanService(vendorSubscriptionRepository, systemSettingService);
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
}
