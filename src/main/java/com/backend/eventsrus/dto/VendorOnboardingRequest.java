package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.EventType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
@NoArgsConstructor
public class VendorOnboardingRequest {

    @NotBlank
    private String businessName;

    @NotEmpty(message = "Select at least one business type")
    private List<BusinessType> businessTypes;

    @NotBlank(message = "Owner name is required")
    private String ownerName;

    private String description;

    @NotBlank(message = "Email is required")
    @Email
    private String contactEmail;

    @NotBlank(message = "Mobile number is required")
    private String phoneNumber;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String postalCode;

    private String country;

    private List<String> operatingAreas;

    // No longer required as of the simplified onboarding form - this field
    // is now hidden there and only ever set later via Account Settings
    // (VendorSettingsRequest). Still mirrors VendorProfile.cateredEventTypes.
    private List<EventType> cateredEventTypes;

    @URL(message = "Enter a valid Facebook page URL")
    private String facebookPageUrl;

    @AssertTrue(message = "You must accept the vendor terms of service")
    private boolean acceptedTerms;

    // Optional for now - eventsrus-ui (Flutter) doesn't send one yet (v3 has
    // no native mobile SDK; would need a WebView-based token flow there), so
    // this can't be @NotBlank without breaking the one real mobile signup
    // path that already works. eventsrus-web's onboarding form does send a
    // real one (see UserService#becomeVendor for the verify-if-present logic).
    private String recaptchaToken;

    // Another vendor's referral code, if this vendor signed up through a
    // referral link (see VendorReferralService#attribute) - optional,
    // silently ignored if missing/invalid, never blocks onboarding.
    private String referralCode;

    // Optional code checked against SystemSettingKey.VENDOR_PROMO_CODE (see
    // PromoCodeService) - a valid code grants the full free trial with no
    // paywall; blank means the vendor goes through the pay-or-skip flow
    // instead (see UserService#becomeVendor).
    private String promoCode;
}
