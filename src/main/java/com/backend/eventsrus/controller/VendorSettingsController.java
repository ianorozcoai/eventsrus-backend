package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.VendorSettingsRequest;
import com.backend.eventsrus.dto.VendorSettingsResponse;
import com.backend.eventsrus.service.UserService;
import com.backend.eventsrus.service.UserService.VendorSettingsFiles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Vendor Account Settings — Business Info, Service Scope & Metrics, Marketplace Escrow & Policies. */
@RestController
@RequestMapping("/api/v1/vendors/me/settings")
@RequiredArgsConstructor
public class VendorSettingsController {

    private final UserService userService;

    @GetMapping
    public VendorSettingsResponse getSettings(Authentication authentication) {
        return userService.getSettings(authentication.getName());
    }

    // Tab 1's logo/ID/selfie/permit and Tab 3's cancellation policy/refund
    // terms are all file uploads, so this whole endpoint is
    // multipart/form-data - same shape as UserController#becomeVendor.
    // Business permit / SEC / DTI / etc. is no longer part of this endpoint -
    // those are managed as their own resource now (any number of them),
    // see VendorLegalDocumentController.
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VendorSettingsResponse updateSettings(
            @ModelAttribute VendorSettingsRequest request,
            @RequestPart(required = false) MultipartFile logo,
            @RequestPart(required = false) MultipartFile idCard,
            @RequestPart(required = false) MultipartFile selfie,
            @RequestPart(required = false) MultipartFile cancellationPolicyFile,
            @RequestPart(required = false) MultipartFile refundTermsFile,
            Authentication authentication) {
        return userService.updateSettings(
                authentication.getName(),
                request,
                new VendorSettingsFiles(logo, idCard, selfie, cancellationPolicyFile, refundTermsFile));
    }
}
