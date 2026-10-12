package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.SuggestedVendorResponse;
import com.backend.eventsrus.dto.VendorPublicProfileResponse;
import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.service.LeadService;
import com.backend.eventsrus.service.ReviewService;
import com.backend.eventsrus.service.VendorDirectoryService;
import com.backend.eventsrus.service.VendorSearchService;
import java.util.List;
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
    private final VendorSearchService vendorSearchService;
    private final ReviewService reviewService;

    /**
     * Deliberate name search for a planner who already has a specific
     * vendor in mind - distinct from the AI-generated suggestions on an
     * event, which only ever surface vendors the matching algorithm picked.
     * Reuses SuggestedVendorResponse's shape since it already carries
     * everything a result card needs (name, slug, logo, city, badges,
     * rating); vendorType is left unset here since a vendor can have
     * several and this isn't grouped by category the way suggestions are -
     * businessTypes carries the full list instead, for display.
     */
    @GetMapping("/search")
    public List<SuggestedVendorResponse> searchVendors(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) BusinessType businessType) {
        return vendorSearchService.searchVendors(query, businessType).stream()
                .map(this::toSuggestedVendorResponse)
                .toList();
    }

    private SuggestedVendorResponse toSuggestedVendorResponse(VendorProfile vendor) {
        ReviewService.RatingSummary ratings = reviewService.ratingSummary(vendor.getUser().getId());
        return SuggestedVendorResponse.builder()
                .vendorProfileId(vendor.getId())
                .businessName(vendor.getBusinessName())
                .businessTypes(vendor.getBusinessTypes())
                .slug(vendor.getSlug())
                .logoImageUrl(vendor.getLogoImageUrl())
                .city(vendor.getCity())
                .verified(vendor.isVerified())
                .topVendor(vendor.isTopVendor())
                .averageRating(ratings.averageRating())
                .reviewCount(ratings.reviewCount())
                .build();
    }

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
