package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.InvalidPromoCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromoCodeServiceTest {

    @Mock
    private SystemSettingService systemSettingService;

    private PromoCodeService service;

    @BeforeEach
    void setUp() {
        service = new PromoCodeService(systemSettingService);
        lenient().when(systemSettingService.getString(SystemSettingKey.VENDOR_PROMO_CODE)).thenReturn("FREE3M");
        lenient().when(systemSettingService.getString(SystemSettingKey.VENDOR_PROMO_CODE_BOOKINGS))
                .thenReturn("FREE3BOOKINGS");
    }

    @Test
    void theFreeTrialCodeIsStillAcceptedAndIsNotTheBookingsGoalCode() {
        assertThat(service.isValidIfPresent(" free3m ")).isTrue();
        assertThat(service.isBookingsGoalCode(" free3m ")).isFalse();
    }

    @Test
    void theBookingsGoalCodeIsValidButIsNotTheFreeTrialCode() {
        assertThat(service.isValidIfPresent("free3bookings")).isFalse();
        assertThat(service.isBookingsGoalCode("free3bookings")).isTrue();
    }

    @Test
    void aBlankCodeIsNeitherAndAnUnknownCodeIsRejected() {
        assertThat(service.isValidIfPresent(null)).isFalse();
        assertThat(service.isValidIfPresent("  ")).isFalse();
        assertThat(service.isBookingsGoalCode(null)).isFalse();
        assertThatThrownBy(() -> service.isValidIfPresent("NOPE")).isInstanceOf(InvalidPromoCodeException.class);
    }
}
