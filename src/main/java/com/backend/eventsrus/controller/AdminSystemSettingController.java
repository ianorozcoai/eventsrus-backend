package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.SystemSettingResponse;
import com.backend.eventsrus.dto.UpdateSystemSettingRequest;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.InvalidSystemSettingException;
import com.backend.eventsrus.service.SystemSettingService;
import com.backend.eventsrus.service.VendorSubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin module's "System Settings" screen - protected by SecurityConfig's
 * existing /api/v1/admin/** -> hasRole("ADMIN") rule, same as every other
 * admin endpoint.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
public class AdminSystemSettingController {

    private final SystemSettingService systemSettingService;
    private final VendorSubscriptionService vendorSubscriptionService;

    @GetMapping
    public List<SystemSettingResponse> list() {
        return systemSettingService.listAll();
    }

    @PutMapping("/{key}")
    public SystemSettingResponse update(@PathVariable String key, @Valid @RequestBody UpdateSystemSettingRequest request) {
        // Pushed to PayPal before the setting itself is saved, so a PayPal
        // failure leaves the old price in effect everywhere rather than the
        // display and the real charge disagreeing - see
        // VendorSubscriptionService#syncProPricingToPayPal.
        if (SystemSettingKey.VENDOR_PRO_MONTHLY_PRICE.key().equals(key)) {
            int newPrice;
            try {
                newPrice = Integer.parseInt(request.getValue().trim());
            } catch (NumberFormatException e) {
                throw new InvalidSystemSettingException("Value must be a whole number.");
            }
            vendorSubscriptionService.syncProPricingToPayPal(newPrice);
        }
        return systemSettingService.updateValue(key, request.getValue());
    }
}
