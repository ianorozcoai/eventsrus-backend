package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.CreateSubscriptionRequest;
import com.backend.eventsrus.dto.CreateSubscriptionResponse;
import com.backend.eventsrus.dto.PayPalApproveSubscriptionRequest;
import com.backend.eventsrus.dto.SubscriptionStatusResponse;
import com.backend.eventsrus.dto.VendorBillingHistoryEntryResponse;
import com.backend.eventsrus.enums.BillingCycle;
import com.backend.eventsrus.service.VendorBillingHistoryService;
import com.backend.eventsrus.service.VendorSubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    /** Records a subscription the JS SDK Smart Buttons already created client-side - see VendorSubscriptionService#recordApprovedSubscription. */
    @PostMapping("/paypal-approve")
    public SubscriptionStatusResponse paypalApprove(
            @Valid @RequestBody PayPalApproveSubscriptionRequest request, Authentication authentication) {
        return vendorSubscriptionService.recordApprovedSubscription(authentication.getName(), request.getPaypalSubscriptionId());
    }

    @GetMapping
    public SubscriptionStatusResponse status(Authentication authentication) {
        return vendorSubscriptionService.getStatus(authentication.getName());
    }

    /** "Upload Payment Screenshot" on the GCash popup - see VendorSubscriptionService#submitGcashPayment. */
    @PostMapping(path = "/gcash-payment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SubscriptionStatusResponse gcashPayment(
            @RequestParam BillingCycle billingCycle, @RequestPart MultipartFile screenshot,
            @RequestParam(required = false) String vendorRemarks, Authentication authentication) {
        return vendorSubscriptionService.submitGcashPayment(authentication.getName(), billingCycle, screenshot, vendorRemarks);
    }

    @GetMapping("/history")
    public List<VendorBillingHistoryEntryResponse> history(Authentication authentication) {
        return vendorBillingHistoryService.listForVendor(authentication.getName());
    }

    /** "Got it" on the one-time Welcome to PRO popup - see VendorSubscriptionService#markProWelcomeShown. */
    @PostMapping("/welcome-shown")
    public void welcomeShown(Authentication authentication) {
        vendorSubscriptionService.markProWelcomeShown(authentication.getName());
    }
}
