package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.VendorPublicProfileResponse;
import com.backend.eventsrus.service.LeadService;
import com.backend.eventsrus.service.VendorDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorDirectoryController {

    private final VendorDirectoryService vendorDirectoryService;
    private final LeadService leadService;

    /**
     * Public vendor storefront. When {@code eventId} is supplied (the planner
     * arrived here from one of their events), this also records/refreshes a
     * Lead for the vendor.
     */
    @GetMapping("/{slug}")
    public VendorPublicProfileResponse getVendorProfile(
            @PathVariable String slug,
            @RequestParam(required = false) Long eventId,
            Authentication authentication) {
        VendorPublicProfileResponse profile = vendorDirectoryService.getPublicProfile(slug);
        if (eventId != null && authentication != null) {
            leadService.recordVisit(authentication.getName(), profile.getVendorUserId(), eventId);
        }
        return profile;
    }
}
