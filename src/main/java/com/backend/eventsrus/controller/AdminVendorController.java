package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AdminVendorListItemResponse;
import com.backend.eventsrus.dto.VendorVerificationDocumentsResponse;
import com.backend.eventsrus.service.ReviewService;
import com.backend.eventsrus.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real vendor verification review, protected by SecurityConfig's existing
 * /api/v1/admin/** -> hasRole("ADMIN") rule. Uploading documents at
 * onboarding no longer implies a vendor is verified - an admin has to
 * actually look at what was submitted here and call #verify before the
 * storefront's "Verified Vendor" badge appears (see VendorDirectoryService).
 */
@RestController
@RequestMapping("/api/v1/admin/vendors")
@RequiredArgsConstructor
public class AdminVendorController {

    private final UserService userService;
    private final ReviewService reviewService;

    /** The review queue - every vendor, regardless of verification state. */
    @GetMapping
    public List<AdminVendorListItemResponse> list() {
        return userService.listVendorsForAdmin().stream()
                .map(v -> AdminVendorListItemResponse.builder()
                        .vendorUserId(v.vendorUserId())
                        .businessName(v.businessName())
                        .ownerName(v.ownerName())
                        .contactEmail(v.contactEmail())
                        .phoneNumber(v.phoneNumber())
                        .businessType(v.businessType())
                        .slug(v.slug())
                        .city(v.city())
                        .state(v.state())
                        .operatingAreas(v.operatingAreas())
                        .hasIdCard(v.hasIdCard())
                        .hasSelfie(v.hasSelfie())
                        .legalDocumentCount(v.legalDocumentCount())
                        .verified(v.verified())
                        .verifiedAt(v.verifiedAt())
                        .verifiedByAdmin(v.verifiedByAdmin())
                        .topVendor(v.topVendor())
                        .createdAt(v.createdAt())
                        .build())
                .toList();
    }

    @GetMapping("/{userId}/verification-documents")
    public VendorVerificationDocumentsResponse getVerificationDocuments(@PathVariable Long userId) {
        UserService.VendorVerificationDocuments documents = userService.getVerificationDocuments(userId);
        return VendorVerificationDocumentsResponse.builder()
                .vendorUserId(documents.vendorUserId())
                .businessName(documents.businessName())
                .ownerName(documents.ownerName())
                .idCardUrl(documents.idCardUrl())
                .selfieUrl(documents.selfieUrl())
                .legalDocuments(documents.legalDocuments())
                .verified(documents.verified())
                .verifiedAt(documents.verifiedAt())
                .verifiedByAdmin(documents.verifiedByAdmin())
                .topVendor(documents.topVendor())
                .reviews(reviewService.listAll(userId))
                .build();
    }

    /**
     * adminUsername identifies whoever clicked the button in eventsrus-web's
     * own admin session (see that project's AdminSession) - not a real
     * backend User, just an audit-trail label (there's no per-admin backend
     * identity yet, see AdminInternalAuthController).
     */
    @PostMapping("/{userId}/verify")
    public void verify(@PathVariable Long userId, @RequestParam(required = false) String adminUsername) {
        userService.verifyVendor(userId, adminUsername);
    }

    @PostMapping("/{userId}/unverify")
    public void unverify(@PathVariable Long userId) {
        userService.unverifyVendor(userId);
    }

    @PostMapping("/{userId}/mark-top")
    public void markTop(@PathVariable Long userId) {
        userService.setTopVendor(userId, true);
    }

    @PostMapping("/{userId}/unmark-top")
    public void unmarkTop(@PathVariable Long userId) {
        userService.setTopVendor(userId, false);
    }
}
