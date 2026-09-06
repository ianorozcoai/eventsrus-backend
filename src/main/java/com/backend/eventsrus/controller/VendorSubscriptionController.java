package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.CreateSubscriptionRequest;
import com.backend.eventsrus.dto.CreateSubscriptionResponse;
import com.backend.eventsrus.dto.SubscriptionStatusResponse;
import com.backend.eventsrus.dto.VendorBillingHistoryEntryResponse;
import com.backend.eventsrus.service.VendorBillingHistoryService;
import com.backend.eventsrus.service.VendorSubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vendors/me/subscription")
@RequiredArgsConstructor
public class VendorSubscriptionController {

    private final VendorSubscriptionService vendorSubscriptionService;
    private final VendorBillingHistoryService vendorBillingHistoryService;

    @PostMapping
    public CreateSubscriptionResponse create(
            @Valid @RequestBody CreateSubscriptionRequest request, Authentication authentication) {
        return vendorSubscriptionService.createSubscription(authentication.getName(), request);
    }

    @GetMapping("/confirm")
    public SubscriptionStatusResponse confirm(
            @RequestParam Long vendorSubscriptionId, Authentication authentication) {
        return vendorSubscriptionService.confirmSubscription(authentication.getName(), vendorSubscriptionId);
    }

    @GetMapping
    public SubscriptionStatusResponse status(Authentication authentication) {
        return vendorSubscriptionService.getStatus(authentication.getName());
    }

    @GetMapping("/history")
    public List<VendorBillingHistoryEntryResponse> history(Authentication authentication) {
        return vendorBillingHistoryService.listForVendor(authentication.getName());
    }
}
