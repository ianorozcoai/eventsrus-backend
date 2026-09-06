package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.EventType;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorSettingsResponse {

    private String businessName;
    private String ownerName;
    private BusinessType businessType;
    private String contactEmail;
    private String phoneNumber;
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

    private BusinessType primaryCategory;
    private Integer maxGuestCapacity;
    private BigDecimal basePrice;
    private Integer leadTimeDays;
    private String storefrontOverview;
    private List<String> operatingAreas;
    private List<EventType> cateredEventTypes;

    private String cancellationPolicyUrl;
    private String refundTermsUrl;

    private String paymentInstructions;
}
