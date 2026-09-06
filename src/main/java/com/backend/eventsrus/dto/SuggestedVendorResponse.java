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
}
