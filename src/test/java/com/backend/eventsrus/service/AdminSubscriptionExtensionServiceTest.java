package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.dto.AdminExtendAccessResponse;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.exception.InvalidAccessExtensionException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.model.VendorSubscriptionEvent;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorSubscriptionEventRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminSubscriptionExtensionServiceTest {

    private static final long VENDOR_ID = 9L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private VendorSubscriptionRepository vendorSubscriptionRepository;
    @Mock
    private VendorSubscriptionEventRepository vendorSubscriptionEventRepository;
    @Mock
    private VendorPlanService vendorPlanService;
    @Mock
    private NotificationService notificationService;

    private AdminSubscriptionExtensionService service;
    private User vendor;

    @BeforeEach
    void setUp() {
        service = new AdminSubscriptionExtensionService(userRepository, vendorSubscriptionRepository,
                vendorSubscriptionEventRepository, vendorPlanService, notificationService);
        vendor = User.builder().id(VENDOR_ID).role(Role.VENDOR).build();
        lenient().when(userRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));
    }

    private VendorSubscription row(BillingSource source, SubscriptionStatus status, Instant end, int carry) {
        VendorSubscription row = VendorSubscription.builder()
                .user(vendor).plan(PlanTier.PRO).billingSource(source).status(status)
                .currentPeriodStart(Instant.now().minus(60, ChronoUnit.DAYS)).currentPeriodEnd(end)
                .carryOverDays(carry).build();
        row.setId(77L);
        when(vendorSubscriptionRepository.findFirstByUserIdOrderByCurrentPeriodStartDesc(VENDOR_ID))
                .thenReturn(Optional.of(row));
        lenient().when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));
        return row;
    }

    private void effectiveEndWillBe(Instant end) {
        when(vendorPlanService.getEffectivePlan(VENDOR_ID)).thenReturn(
                new VendorPlanService.EffectivePlan(PlanTier.PRO, end, false, false, false, null, BillingSource.PAYPAL));
    }

    @Test
    void daysAreAddedOnTopOfTheCurrentEndDate() {
        VendorSubscription r = row(BillingSource.PAYPAL, SubscriptionStatus.ACTIVE, Instant.now().plus(10, ChronoUnit.DAYS), 4);
        Instant newEnd = Instant.now().plus(44, ChronoUnit.DAYS);
        effectiveEndWillBe(newEnd);

        AdminExtendAccessResponse response = service.extend(VENDOR_ID, 30, "Goodwill", "ian");

        assertThat(r.getCarryOverDays()).isEqualTo(34);
        assertThat(response.getDaysAdded()).isEqualTo(30);
        assertThat(response.getAccessEndsAt()).isEqualTo(newEnd);
    }

    @Test
    void anAlreadyEndedPlanCountsTheNewDaysFromTodayNotFromTheOldEndDate() {
        // Ended 20 days ago: 20 days just to get back to today (nearest whole day), then the 15 requested ones.
        VendorSubscription r = row(BillingSource.GCASH, SubscriptionStatus.ACTIVE,
                Instant.now().minus(20, ChronoUnit.DAYS).minusSeconds(60), 0);
        effectiveEndWillBe(Instant.now().plus(15, ChronoUnit.DAYS));

        service.extend(VENDOR_ID, 15, "Payment problem being fixed", "ian");

        assertThat(r.getCarryOverDays()).isEqualTo(35);
    }

    @Test
    void aSupplierWithNoPlanGetsAFreePeriod() {
        when(vendorSubscriptionRepository.findFirstByUserIdOrderByCurrentPeriodStartDesc(VENDOR_ID))
                .thenReturn(Optional.empty());
        when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> {
            VendorSubscription saved = i.getArgument(0);
            saved.setId(5L);
            return saved;
        });
        effectiveEndWillBe(Instant.now().plus(30, ChronoUnit.DAYS));

        AdminExtendAccessResponse response = service.extend(VENDOR_ID, 30, "Pilot supplier", "ian");

        ArgumentCaptor<VendorSubscription> saved = ArgumentCaptor.forClass(VendorSubscription.class);
        verify(vendorSubscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getBillingSource()).isEqualTo(BillingSource.FREE_GRANT);
        assertThat(saved.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getValue().getCurrentPeriodEnd())
                .isBetween(Instant.now().plus(30, ChronoUnit.DAYS).minusSeconds(60),
                        Instant.now().plus(30, ChronoUnit.DAYS).plusSeconds(60));
        assertThat(response.getBillingSource()).isEqualTo(BillingSource.FREE_GRANT);
    }

    @Test
    void everyExtensionIsLoggedWithWhoHowManyDaysAndWhyAndTheSupplierIsNotified() {
        row(BillingSource.PAYPAL, SubscriptionStatus.ACTIVE, Instant.now().plus(5, ChronoUnit.DAYS), 0);
        effectiveEndWillBe(Instant.now().plus(35, ChronoUnit.DAYS));

        service.extend(VENDOR_ID, 30, "  Waiting for the bank  ", "ian");

        ArgumentCaptor<VendorSubscriptionEvent> event = ArgumentCaptor.forClass(VendorSubscriptionEvent.class);
        verify(vendorSubscriptionEventRepository).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("ADMIN_EXTEND_ACCESS");
        assertThat(event.getValue().getPayload()).contains("by=ian", "days=30", "reason=Waiting for the bank");
        verify(notificationService).notify(eq(vendor), eq(NotificationType.SUBSCRIPTION_EXTENDED),
                eq("Your access was extended"), any(), eq("SUBSCRIPTION"), any());
    }

    @Test
    void aGcashPaymentUnderReviewBlocksTheExtension() {
        row(BillingSource.GCASH, SubscriptionStatus.PAYMENT_VERIFICATION, Instant.now().plus(3, ChronoUnit.DAYS), 0);

        assertThatThrownBy(() -> service.extend(VENDOR_ID, 10, "x", "ian"))
                .isInstanceOf(InvalidAccessExtensionException.class)
                .hasMessageContaining("waiting for review");

        verify(vendorSubscriptionRepository, never()).save(any());
    }

    @Test
    void daysMustBeBetweenOneAndAYearAndAReasonIsRequired() {
        assertThatThrownBy(() -> service.extend(VENDOR_ID, 0, "x", "ian"))
                .isInstanceOf(InvalidAccessExtensionException.class);
        assertThatThrownBy(() -> service.extend(VENDOR_ID, 366, "x", "ian"))
                .isInstanceOf(InvalidAccessExtensionException.class);
        assertThatThrownBy(() -> service.extend(VENDOR_ID, 10, "   ", "ian"))
                .isInstanceOf(InvalidAccessExtensionException.class)
                .hasMessageContaining("reason");

        verify(vendorSubscriptionRepository, never()).save(any());
    }

    @Test
    void onlySuppliersCanBeExtended() {
        vendor.setRole(Role.PLANNER);

        assertThatThrownBy(() -> service.extend(VENDOR_ID, 10, "x", "ian"))
                .isInstanceOf(InvalidAccessExtensionException.class)
                .hasMessageContaining("not found");
    }
}
