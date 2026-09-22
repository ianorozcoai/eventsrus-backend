package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.EventType;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
@NoArgsConstructor
public class VendorSettingsRequest {

    // Tab 1 — Business Info & Credentials
    private String businessName;
    private String description;
    private String ownerName;
    private BusinessType businessType;
    private String contactEmail;
    private String phoneNumber;
    @URL(message = "Enter a valid Facebook page URL")
    private String facebookPageUrl;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;

    // Tab 2 — Service Scope & Metrics
    private BusinessType primaryCategory;
    private Integer maxGuestCapacity;
    private Integer maxCustomersPerDay;
    private BigDecimal basePrice;
    private Integer leadTimeDays;
    private String storefrontOverview;
    private List<String> operatingAreas;
    private List<EventType> cateredEventTypes;

    // Tab 3 — Marketplace Escrow & Policies: cancellation policy and refund
    // terms are vendor-uploaded PDFs now, not text - see
    // VendorSettingsController#updateSettings, which takes them as separate
    // multipart file parts rather than fields on this DTO (same pattern as
    // VendorOnboardingRequest's logo/idCard/selfie/businessPermit).

    // Tab 4 — Payment Methods: the free-text instructions field. The QR
    // code uploads themselves go through VendorPaymentMethodController, not
    // this DTO - same reasoning as tab 3's file uploads above.
    private String paymentInstructions;
}
