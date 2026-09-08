package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.VendorReferralOverviewResponse;
import com.backend.eventsrus.service.VendorReferralService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vendors/me/referrals")
@RequiredArgsConstructor
public class VendorReferralController {

    private final VendorReferralService vendorReferralService;

    @GetMapping
    public VendorReferralOverviewResponse overview(Authentication authentication) {
        return vendorReferralService.getOverview(authentication.getName());
    }
}
