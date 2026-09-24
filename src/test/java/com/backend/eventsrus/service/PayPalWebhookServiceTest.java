package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.VendorSubscriptionEventRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

/**
 * PAYMENT.SALE.DENIED (a failed subscription renewal charge) used to fall
 * through the default case entirely - logged for audit, no notification,
 * no status change. This locks in the fix: the vendor and admin get told,
 * but status is deliberately left alone since PayPal's own retry schedule
 * is what eventually fires BILLING.SUBSCRIPTION.SUSPENDED/CANCELLED.
 */
@ExtendWith(MockitoExtension.class)
class PayPalWebhookServiceTest {

    private static final String PAYPAL_SUBSCRIPTION_ID = "I-TESTSUB123";

    @Mock
    private VendorSubscriptionRepository vendorSubscriptionRepository;
    @Mock
    private VendorSubscriptionEventRepository vendorSubscriptionEventRepository;
    @Mock
    private PayPalSubscriptionClient payPalSubscriptionClient;
    @Mock
    private VendorBillingHistoryService vendorBillingHistoryService;
    @Mock
    private VendorReferralService vendorReferralService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AdminNotificationEmailService adminNotificationEmailService;

    private PayPalWebhookService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new PayPalWebhookService(vendorSubscriptionRepository, vendorSubscriptionEventRepository,
                payPalSubscriptionClient, vendorBillingHistoryService, vendorReferralService, notificationService,
                adminNotificationEmailService);
    }

    @Test
    void paymentSaleDeniedNotifiesVendorAndAdminWithoutChangingStatus() {
        User vendor = User.builder().id(42L).email("vendor@example.com").build();
        VendorSubscription subscription = VendorSubscription.builder()
                .user(vendor)
                .status(SubscriptionStatus.ACTIVE)
                .paypalSubscriptionId(PAYPAL_SUBSCRIPTION_ID)
                .build();

        when(vendorSubscriptionEventRepository.existsByPaypalEventId("WH-1")).thenReturn(false);
        when(vendorSubscriptionRepository.findByPaypalSubscriptionId(PAYPAL_SUBSCRIPTION_ID))
                .thenReturn(java.util.Optional.of(subscription));

        String payload = """
                {
                  "id": "WH-1",
                  "event_type": "PAYMENT.SALE.DENIED",
                  "resource": { "id": "SALE-1", "billing_agreement_id": "%s" }
                }
                """.formatted(PAYPAL_SUBSCRIPTION_ID);

        service.handle(objectMapper.readTree(payload));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(notificationService).notify(eq(vendor), eq(NotificationType.SUBSCRIPTION_PAYMENT_FAILED),
                any(), any(), eq("SUBSCRIPTION"), eq(subscription.getId()));
        verify(adminNotificationEmailService).notifyPaymentFailed(vendor);
        verify(vendorSubscriptionRepository, times(1)).save(subscription);
    }

    @Test
    void paymentSaleDeniedForUnknownSubscriptionIsLoggedButDoesNotNotify() {
        when(vendorSubscriptionEventRepository.existsByPaypalEventId("WH-2")).thenReturn(false);
        when(vendorSubscriptionRepository.findByPaypalSubscriptionId("I-UNKNOWN")).thenReturn(java.util.Optional.empty());

        String payload = """
                {
                  "id": "WH-2",
                  "event_type": "PAYMENT.SALE.DENIED",
                  "resource": { "id": "SALE-2", "billing_agreement_id": "I-UNKNOWN" }
                }
                """;

        service.handle(objectMapper.readTree(payload));

        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
        verify(adminNotificationEmailService, never()).notifyPaymentFailed(any());
    }
}
