package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.BillingCycle;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.model.VendorSubscriptionEvent;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorSubscriptionEventRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RevenueCatWebhookServiceTest {

    private static final long USER_ID = 42L;
    private static final long PURCHASED = 1_800_000_000_000L;
    private static final long QUARTER_LATER = PURCHASED + 91L * 24 * 3600 * 1000;

    @Mock
    private UserRepository userRepository;
    @Mock
    private VendorSubscriptionRepository vendorSubscriptionRepository;
    @Mock
    private VendorSubscriptionEventRepository vendorSubscriptionEventRepository;
    @Mock
    private VendorBillingHistoryService vendorBillingHistoryService;
    @Mock
    private VendorReferralService vendorReferralService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private VendorPlanService vendorPlanService;

    private final JsonMapper json = JsonMapper.builder().build();
    private RevenueCatWebhookService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RevenueCatWebhookService(
                userRepository, vendorSubscriptionRepository, vendorSubscriptionEventRepository,
                vendorBillingHistoryService, vendorReferralService, notificationService, vendorPlanService);
        user = new User();
        user.setId(USER_ID);
    }

    private JsonNode event(String id, String type, String extra) {
        return json.readTree("{\"api_version\":\"1.0\",\"event\":{\"id\":\"" + id + "\",\"type\":\"" + type
                + "\",\"app_user_id\":\"" + USER_ID + "\"" + (extra.isEmpty() ? "" : "," + extra) + "}}");
    }

    private void knownUserNoRow() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(vendorSubscriptionEventRepository.existsByPaypalEventId(any())).thenReturn(false);
        when(vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(
                USER_ID, BillingSource.GOOGLE_PLAY)).thenReturn(Optional.empty());
    }

    private VendorSubscription activeRow(Instant end) {
        return VendorSubscription.builder()
                .user(user).plan(PlanTier.PRO).billingSource(BillingSource.GOOGLE_PLAY)
                .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.QUARTERLY)
                .currentPeriodStart(Instant.ofEpochMilli(PURCHASED)).currentPeriodEnd(end).build();
    }

    private void knownUserWithRow(VendorSubscription row) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(vendorSubscriptionEventRepository.existsByPaypalEventId(any())).thenReturn(false);
        when(vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(
                USER_ID, BillingSource.GOOGLE_PLAY)).thenReturn(Optional.of(row));
    }

    private static final String PURCHASE_FIELDS = "\"product_id\":\"supplier_pro:quarterly\","
            + "\"purchased_at_ms\":" + PURCHASED + ",\"expiration_at_ms\":" + QUARTER_LATER
            + ",\"transaction_id\":\"GPA.1\",\"currency\":\"PHP\",\"price_in_purchased_currency\":4500.0";

    @Test
    void initialPurchaseCreatesActiveGooglePlayRowAndHistory() {
        knownUserNoRow();
        when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(event("evt-1", "INITIAL_PURCHASE", PURCHASE_FIELDS));

        ArgumentCaptor<VendorSubscription> saved = ArgumentCaptor.forClass(VendorSubscription.class);
        verify(vendorSubscriptionRepository).save(saved.capture());
        VendorSubscription row = saved.getValue();
        assertThat(row.getBillingSource()).isEqualTo(BillingSource.GOOGLE_PLAY);
        assertThat(row.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(row.getPlan()).isEqualTo(PlanTier.PRO);
        assertThat(row.getBillingCycle()).isEqualTo(BillingCycle.QUARTERLY);
        assertThat(row.getCurrentPeriodEnd()).isEqualTo(Instant.ofEpochMilli(QUARTER_LATER));
        verify(vendorBillingHistoryService).recordPayment(
                eq(row), eq(new BigDecimal("4500.0")), eq("PHP"), eq("GPA.1"),
                eq(Instant.ofEpochMilli(PURCHASED)), eq(Instant.ofEpochMilli(QUARTER_LATER)),
                eq(Instant.ofEpochMilli(PURCHASED)), eq(BillingSource.GOOGLE_PLAY));
        verify(vendorReferralService).convertIfPending(USER_ID);
        verify(vendorSubscriptionEventRepository).save(any(VendorSubscriptionEvent.class));
    }

    @Test
    void initialPurchaseKeepsDaysLeftOnThePreviousPlanAsCarryOver() {
        knownUserNoRow();
        when(vendorPlanService.remainingDays(USER_ID)).thenReturn(40);
        when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(event("evt-co", "INITIAL_PURCHASE", PURCHASE_FIELDS));

        ArgumentCaptor<VendorSubscription> saved = ArgumentCaptor.forClass(VendorSubscription.class);
        verify(vendorSubscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getCarryOverDays()).isEqualTo(40);
    }

    @Test
    void renewalNeverChangesCarryOver() {
        VendorSubscription row = activeRow(Instant.ofEpochMilli(PURCHASED + 1000));
        row.setCarryOverDays(12);
        knownUserWithRow(row);
        when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(event("evt-co2", "RENEWAL", PURCHASE_FIELDS));

        assertThat(row.getCarryOverDays()).isEqualTo(12);
        verify(vendorPlanService, never()).remainingDays(any());
    }

    @Test
    void repeatedInitialPurchaseWhileThePlayPlanStillRunsNeverCountsTheSameDaysTwice() {
        VendorSubscription row = activeRow(Instant.now().plusSeconds(30L * 86400));
        row.setCarryOverDays(5);
        knownUserWithRow(row);
        when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(event("evt-co3", "INITIAL_PURCHASE", PURCHASE_FIELDS));

        assertThat(row.getCarryOverDays()).isEqualTo(5);
        verify(vendorPlanService, never()).remainingDays(any());
    }

    @Test
    void renewalExtendsExistingRowAndRecordsPayment() {
        VendorSubscription row = activeRow(Instant.ofEpochMilli(PURCHASED + 1000));
        knownUserWithRow(row);
        when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(event("evt-2", "RENEWAL", PURCHASE_FIELDS));

        assertThat(row.getCurrentPeriodEnd()).isEqualTo(Instant.ofEpochMilli(QUARTER_LATER));
        assertThat(row.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(vendorBillingHistoryService).recordPayment(
                eq(row), any(), eq("PHP"), eq("GPA.1"), any(), any(), any(), eq(BillingSource.GOOGLE_PLAY));
    }

    @Test
    void lateDeliveredEventNeverShortensKnownPeriod() {
        Instant knownEnd = Instant.ofEpochMilli(QUARTER_LATER + 10_000_000L);
        VendorSubscription row = activeRow(knownEnd);
        knownUserWithRow(row);
        when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(event("evt-3", "RENEWAL", PURCHASE_FIELDS));

        assertThat(row.getCurrentPeriodEnd()).isEqualTo(knownEnd);
    }

    @Test
    void cancellationKeepsAccessUntilPeriodEnd() {
        Instant end = Instant.ofEpochMilli(QUARTER_LATER);
        VendorSubscription row = activeRow(end);
        knownUserWithRow(row);
        when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(event("evt-4", "CANCELLATION", "\"expiration_at_ms\":" + QUARTER_LATER));

        assertThat(row.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(row.getCurrentPeriodEnd()).isEqualTo(end);
        verify(vendorBillingHistoryService, never()).recordPayment(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void expirationMarksExpiredAndSetsEnd() {
        VendorSubscription row = activeRow(Instant.ofEpochMilli(QUARTER_LATER));
        knownUserWithRow(row);
        when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(event("evt-5", "EXPIRATION", "\"expiration_at_ms\":" + (PURCHASED + 5000)));

        assertThat(row.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(row.getCurrentPeriodEnd()).isEqualTo(Instant.ofEpochMilli(PURCHASED + 5000));
    }

    @Test
    void billingIssueNotifiesButKeepsStatus() {
        VendorSubscription row = activeRow(Instant.ofEpochMilli(QUARTER_LATER));
        knownUserWithRow(row);

        service.handle(event("evt-6", "BILLING_ISSUE", ""));

        assertThat(row.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(notificationService).notify(
                eq(user), eq(NotificationType.SUBSCRIPTION_PAYMENT_FAILED), any(), any(), eq("SUBSCRIPTION"), any());
        verify(vendorSubscriptionRepository, never()).save(any());
    }

    @Test
    void duplicateDeliveryIsIgnored() {
        when(vendorSubscriptionEventRepository.existsByPaypalEventId("evt-7")).thenReturn(true);

        service.handle(event("evt-7", "INITIAL_PURCHASE", PURCHASE_FIELDS));

        verify(vendorSubscriptionRepository, never()).save(any());
        verify(vendorSubscriptionEventRepository, never()).save(any());
    }

    @Test
    void unknownUserIsLoggedAsEventWithoutSubscription() {
        when(vendorSubscriptionEventRepository.existsByPaypalEventId(any())).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.handle(event("evt-8", "INITIAL_PURCHASE", PURCHASE_FIELDS));

        verify(vendorSubscriptionRepository, never()).save(any());
        ArgumentCaptor<VendorSubscriptionEvent> saved = ArgumentCaptor.forClass(VendorSubscriptionEvent.class);
        verify(vendorSubscriptionEventRepository).save(saved.capture());
        assertThat(saved.getValue().getVendorSubscription()).isNull();
    }

    @Test
    void anonymousAppUserIdFallsBackToNumericAlias() {
        when(vendorSubscriptionEventRepository.existsByPaypalEventId(any())).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(vendorSubscriptionRepository.findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(
                USER_ID, BillingSource.GOOGLE_PLAY)).thenReturn(Optional.empty());
        when(vendorSubscriptionRepository.save(any(VendorSubscription.class))).thenAnswer(i -> i.getArgument(0));
        JsonNode body = json.readTree("{\"event\":{\"id\":\"evt-9\",\"type\":\"INITIAL_PURCHASE\","
                + "\"app_user_id\":\"$RCAnonymousID:abc\",\"aliases\":[\"$RCAnonymousID:abc\",\"" + USER_ID + "\"],"
                + PURCHASE_FIELDS + "}}");

        service.handle(body);

        verify(vendorSubscriptionRepository).save(any(VendorSubscription.class));
    }

    @Test
    void testEventIsLoggedOnly() {
        when(vendorSubscriptionEventRepository.existsByPaypalEventId(any())).thenReturn(false);

        service.handle(event("evt-10", "TEST", ""));

        verify(userRepository, never()).findById(any());
        verify(vendorSubscriptionEventRepository).save(any(VendorSubscriptionEvent.class));
    }

    @Test
    void cycleIsReadFromBasePlanOrInferredFromDuration() {
        Instant s = Instant.ofEpochMilli(PURCHASED);
        assertThat(RevenueCatWebhookService.cycleFor("supplier_pro:semiannual", s, s)).isEqualTo(BillingCycle.SEMI_ANNUAL);
        assertThat(RevenueCatWebhookService.cycleFor("supplier_pro:annual", s, s)).isEqualTo(BillingCycle.ANNUAL);
        assertThat(RevenueCatWebhookService.cycleFor("x", s, s.plusSeconds(200L * 86400))).isEqualTo(BillingCycle.SEMI_ANNUAL);
        assertThat(RevenueCatWebhookService.cycleFor(null, null, null)).isNull();
    }
}
