package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.InvalidPromoCodeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Validates the optional Promo Code field at vendor onboarding (see
 * UserService#becomeVendor) against the single admin-configured code
 * (SystemSettingKey.VENDOR_PROMO_CODE) - unlike a referral code, there's
 * nothing per-vendor here, just one shared code.
 */
@Service
@RequiredArgsConstructor
public class PromoCodeService {

    private final SystemSettingService systemSettingService;

    /**
     * A blank code means no promo was given - returns false, not an error
     * (matches VendorReferralService's own blank-is-fine convention). A
     * non-blank code that doesn't match the configured code is the one real
     * error case - fails fast in becomeVendor, before any uploads happen.
     */
    public boolean isValidIfPresent(String rawPromoCode) {
        if (rawPromoCode == null || rawPromoCode.isBlank()) {
            return false;
        }
        String expected = systemSettingService.getString(SystemSettingKey.VENDOR_PROMO_CODE);
        if (!rawPromoCode.trim().equalsIgnoreCase(expected)) {
            throw new InvalidPromoCodeException("That promo code isn't valid.");
        }
        return true;
    }
}
