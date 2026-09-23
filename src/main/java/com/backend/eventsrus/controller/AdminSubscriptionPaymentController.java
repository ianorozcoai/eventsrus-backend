package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AdminSubscriptionPaymentResponse;
import com.backend.eventsrus.service.VendorSubscriptionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin module's GCash payment-verification queue - protected by
 * SecurityConfig's existing /api/v1/admin/** -> hasRole("ADMIN") rule, same
 * as every other admin endpoint.
 */
@RestController
@RequestMapping("/api/v1/admin/subscription-payments")
@RequiredArgsConstructor
public class AdminSubscriptionPaymentController {

    private final VendorSubscriptionService vendorSubscriptionService;

    @GetMapping
    public List<AdminSubscriptionPaymentResponse> list() {
        return vendorSubscriptionService.listGcashPayments();
    }

    @PostMapping("/{userId}/verify")
    public void verify(@PathVariable Long userId) {
        vendorSubscriptionService.verifyGcashPayment(userId);
    }

    @PostMapping("/{userId}/reject")
    public void reject(@PathVariable Long userId, @RequestParam String reason) {
        vendorSubscriptionService.rejectGcashPayment(userId, reason);
    }
}
