package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AdminReferralResponse;
import com.backend.eventsrus.service.VendorReferralService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real referral oversight for admins - protected by SecurityConfig's
 * existing /api/v1/admin/** -> hasRole("ADMIN") rule. Note: eventsrus-web's
 * admin module (see agents/cto/codebase/eventsrus-web) has no real backend
 * login of its own yet (its username/password auth is entirely local/stub),
 * so there's no UI wired to this today - it's callable now for whoever has
 * a real ADMIN-role JWT, with a proper admin UI a separate follow-up.
 */
@RestController
@RequestMapping("/api/v1/admin/referrals")
@RequiredArgsConstructor
public class AdminReferralController {

    private final VendorReferralService vendorReferralService;

    @GetMapping
    public List<AdminReferralResponse> list() {
        return vendorReferralService.listAllForAdmin();
    }

    @PostMapping("/{id}/mark-paid")
    public void markPaid(@PathVariable Long id) {
        vendorReferralService.markPaid(id);
    }
}
