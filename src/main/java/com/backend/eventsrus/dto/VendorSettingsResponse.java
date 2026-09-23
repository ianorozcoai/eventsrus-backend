package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.EventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorSettingsResponse {

    // Needed client-side to link to the vendor's own public storefront
    // (GET /api/v1/vendors/{slug}) - there's no other authenticated endpoint
    // that exposes it.
    private String slug;

    private String businessName;
    private String description;
    private String ownerName;
    private List<BusinessType> businessTypes;
    private String contactEmail;
    private String phoneNumber;
    private String facebookPageUrl;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String logoImageUrl;
    private String idCardUrl;
    private String selfieUrl;
    // businessPermitUrl is gone - legal documents (any number of them) are
    // now their own resource, fetched via GET /api/v1/vendors/me/legal-documents
    // (VendorLegalDocumentController), same pattern paymentMethods already uses.

    // Real, admin-set verification state (see AdminVendorController#verify) -
    // lets the vendor see their own review status on their Settings page
    // instead of only finding out via the storefront badge.
    private boolean verified;
    private Instant verifiedAt;

    private Integer maxGuestCapacity;
    private Integer maxCustomersPerDay;
    private BigDecimal basePrice;
    private Integer leadTimeDays;
    private String storefrontOverview;
    private List<String> operatingAreas;
    private List<EventType> cateredEventTypes;

    private String cancellationPolicyUrl;
    private String refundTermsUrl;

    private String paymentInstructions;
}
