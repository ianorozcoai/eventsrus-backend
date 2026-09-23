package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AdminReferralResponse;
import com.backend.eventsrus.service.VendorReferralService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Real referral oversight for admins - protected by SecurityConfig's
 * existing /api/v1/admin/** -> hasRole("ADMIN") rule. Backed by
 * eventsrus-web's admin/referrals.html (AdminReferralWebController).
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

    @PostMapping(path = "/{id}/mark-paid", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void markPaid(
            @PathVariable Long id,
            @RequestParam(required = false) String remarks,
            @RequestParam(required = false) MultipartFile proof) {
        vendorReferralService.markPaid(id, remarks, proof);
    }
}
