package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.VendorDashboardResponse;
import com.backend.eventsrus.service.VendorDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vendors/me/dashboard")
@RequiredArgsConstructor
public class VendorDashboardController {

    private final VendorDashboardService vendorDashboardService;

    @GetMapping
    public VendorDashboardResponse getDashboard(Authentication authentication) {
        return vendorDashboardService.getDashboard(authentication.getName());
    }
}
