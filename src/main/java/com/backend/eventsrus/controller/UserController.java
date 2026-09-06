package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AuthResponse;
import com.backend.eventsrus.dto.UpdateProfileRequest;
import com.backend.eventsrus.dto.UserProfileResponse;
import com.backend.eventsrus.dto.VendorOnboardingRequest;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.service.AuthService;
import com.backend.eventsrus.service.UserService;
import com.backend.eventsrus.service.UserService.VendorUploadFiles;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    // legalDocumentFiles/legalDocumentTypes/legalDocumentLabels are parallel
    // lists (correlated by index) - a vendor can now attach any number of
    // business-registration documents (DTI, SEC, Mayor's Permit, Barangay
    // Clearance, BIR, ...) at onboarding instead of just one "business
    // permit". Types/labels are plain strings here (not List<LegalDocumentType>)
    // to avoid relying on Spring's per-element enum conversion for
    // multipart form params - UserService parses and defensively falls back
    // to OTHER for anything missing or unrecognized.
    @PatchMapping(path = "/me/vendor", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AuthResponse becomeVendor(
            @Valid @ModelAttribute VendorOnboardingRequest request,
            @RequestPart(required = false) MultipartFile logo,
            @RequestPart(required = false) MultipartFile idCard,
            @RequestPart(required = false) MultipartFile selfie,
            @RequestPart(required = false) List<MultipartFile> legalDocumentFiles,
            @RequestParam(required = false) List<String> legalDocumentTypes,
            @RequestParam(required = false) List<String> legalDocumentLabels,
            Authentication authentication) {
        User user = userService.becomeVendor(
                authentication.getName(),
                request,
                new VendorUploadFiles(
                        logo, idCard, selfie, legalDocumentFiles, legalDocumentTypes, legalDocumentLabels));
        return authService.issueTokenFor(user);
    }

    @GetMapping("/me/profile")
    public UserProfileResponse getProfile(Authentication authentication) {
        return userService.getProfile(authentication.getName());
    }

    // Returns a fresh token (not just the profile) - email is editable here
    // and is also the JWT subject (JwtService/AuthService), so a caller who
    // changes their own email needs a reissued token or their next request
    // would fail to resolve to any user. Same reasoning as becomeVendor
    // above, which reissues a token after a save that affects the JWT's role
    // claim.
    @PutMapping("/me/profile")
    public AuthResponse updateProfile(
            @Valid @RequestBody UpdateProfileRequest request, Authentication authentication) {
        User user = userService.updateProfile(authentication.getName(), request);
        return authService.issueTokenFor(user);
    }
}
