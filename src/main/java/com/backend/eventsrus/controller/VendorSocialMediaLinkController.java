package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AddVendorSocialMediaLinkRequest;
import com.backend.eventsrus.dto.VendorSocialMediaLinkResponse;
import com.backend.eventsrus.service.VendorSocialMediaLinkService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vendors/me/social-media-links")
@RequiredArgsConstructor
public class VendorSocialMediaLinkController {

    private final VendorSocialMediaLinkService vendorSocialMediaLinkService;

    @GetMapping
    public List<VendorSocialMediaLinkResponse> list(Authentication authentication) {
        return vendorSocialMediaLinkService.listForVendor(authentication.getName());
    }

    @PostMapping
    public VendorSocialMediaLinkResponse create(
            @Valid @RequestBody AddVendorSocialMediaLinkRequest request, Authentication authentication) {
        return vendorSocialMediaLinkService.addLink(authentication.getName(), request.getPlatform(), request.getUrl());
    }

    @DeleteMapping("/{linkId}")
    public void delete(@PathVariable Long linkId, Authentication authentication) {
        vendorSocialMediaLinkService.deleteLink(authentication.getName(), linkId);
    }
}
