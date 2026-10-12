package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SuggestedVendorResponse {

    private BusinessType vendorType;

    /**
     * The vendor's full business types - shown alongside a result card
     * instead of the Verified/Top Supplier badges (most vendors in this
     * marketplace aren't registered businesses and never clear
     * verification, so the badges read as friction rather than signal).
     * Null when this row is a generic category placeholder with no
     * matching vendor yet (vendorProfileId also null).
     */
    private List<BusinessType> businessTypes;

    /**
     * Whether the planner has opted into actively browsing/pursuing this
     * category (EventSuggestedVendor#selected) - same value repeated on
     * every row sharing this vendorType, since selection is a per-category
     * choice, not a per-vendor one.
     */
    private boolean categorySelected;
    /** Optional per-category budget earmark - same repetition note as categorySelected. */
    private BigDecimal categoryBudget;
    /** Whether this category is "essential" for the event's type (see EventService's matrix) vs. "Other Suppliers". */
    private boolean essential;

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
