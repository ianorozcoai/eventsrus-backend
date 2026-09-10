package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SuggestedVendorResponse {

    private BusinessType vendorType;

    /** Null when this is a generic category suggestion (no location given yet). */
    private Long vendorProfileId;
    private String businessName;
    private String slug;
    private String logoImageUrl;
    private String city;
    /** Admin-reviewed "Verified Vendor" - shown as a badge, and these sort first in their category. */
    private boolean verified;
    /** Admin-set "Top Vendor" spotlight - shown as a badge, sorts ahead of everything else in its category. */
    private boolean topVendor;
    /** Aggregate planner rating - null when this vendor has no reviews yet. */
    private Double averageRating;
    private int reviewCount;
}
