package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AdminIncompleteVendorSignupResponse;
import com.backend.eventsrus.dto.AdminReferralResponse;
import com.backend.eventsrus.dto.AdminVendorListItemResponse;
import com.backend.eventsrus.dto.AuthResponse;
import com.backend.eventsrus.dto.VendorVerificationDocumentsResponse;
import com.backend.eventsrus.enums.ReferralStatus;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.service.AuthService;
import com.backend.eventsrus.service.ReviewService;
import com.backend.eventsrus.service.UserService;
import com.backend.eventsrus.service.VendorReferralService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AdminVendorController {

    private final UserService userService;
    private final ReviewService reviewService;
    private final VendorReferralService vendorReferralService;
    private final UserRepository userRepository;
    private final AuthService authService;

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
                        .businessTypes(v.businessTypes())
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
                        .referralCount(v.referralCount())
                        .fakeAccount(v.fakeAccount())
                        .bookingCount(v.bookingCount())
                        .lastLoginAt(v.lastLoginAt())
                        .billingSource(v.billingSource())
                        .planExpired(v.planExpired())
                        .planInGracePeriod(v.planInGracePeriod())
                        .planOverdueSince(v.planOverdueSince())
                        .build())
                .toList();
    }

    /** The other half of the Vendors directory - signed up, never finished onboarding. */
    @GetMapping("/incomplete-signups")
    public List<AdminIncompleteVendorSignupResponse> listIncompleteSignups() {
        return userService.listIncompleteVendorSignupsForAdmin().stream()
                .map(v -> AdminIncompleteVendorSignupResponse.builder()
                        .id(v.id())
                        .firstName(v.firstName())
                        .lastName(v.lastName())
                        .email(v.email())
                        .mobileNumber(v.mobileNumber())
                        .signedUpAt(v.signedUpAt())
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
     * own admin session (see that project's AdminSession) - a real
     * admin_accounts username (see AdminAccountService), just used here as a
     * plain audit-trail label rather than a foreign key.
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

    /** Whichever referral (if any) this vendor was the referred party on - null if they arrived unreferred. */
    @GetMapping("/{userId}/referral")
    public AdminReferralResponse getReferral(@PathVariable Long userId) {
        return vendorReferralService.findForReferredVendor(userId).orElse(null);
    }

    /**
     * Support-desk fix for a referral that was never attributed at signup
     * (e.g. the referred vendor forgot to use the link) - see
     * VendorReferralService#createManualReferral. Unlike normal onboarding
     * attribution, every failure here is a real error the admin sees, and
     * the admin picks the resulting status directly since the referred
     * vendor may already have paid before this gets fixed.
     */
    @PostMapping("/{userId}/referral")
    public void tagReferral(
            @PathVariable Long userId, @RequestParam String referrerCode, @RequestParam ReferralStatus status,
            @RequestParam(required = false) BigDecimal commissionAmount) {
        vendorReferralService.createManualReferral(userId, referrerCode, status, commissionAmount);
    }

    /**
     * "View Dashboard" from the admin Vendors page - lets an admin open a
     * vendor's real dashboard for support/setup purposes without that
     * vendor's own Google login. Reuses the exact same token-issuing path a
     * real login goes through (AuthService#issueTokenFor) rather than
     * inventing a second one - the resulting token is a completely normal
     * vendor-role JWT, indistinguishable from one issued at real login.
     * adminUsername is the same plain audit-trail label #verify already
     * uses, not a foreign key.
     */
    @PostMapping("/{userId}/impersonate")
    public AuthResponse impersonate(@PathVariable Long userId, @RequestParam(required = false) String adminUsername) {
        User vendor = userRepository.findById(userId)
                .filter(u -> u.getRole() == Role.VENDOR)
                .orElseThrow(() -> new IllegalStateException("Vendor not found: " + userId));
        log.info("Admin '{}' opened vendor dashboard for userId={} ({})", adminUsername, userId, vendor.getEmail());
        return authService.issueTokenFor(vendor);
    }
}
