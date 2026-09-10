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

@Getter
@Setter
@NoArgsConstructor
public class VendorOnboardingRequest {

    @NotBlank
    private String businessName;

    private BusinessType businessType;

    private String ownerName;

    private String description;

    @Email
    private String contactEmail;

    private String phoneNumber;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String postalCode;

    private String country;

    private List<String> operatingAreas;

    // Which event types this vendor caters to - mandatory at onboarding so
    // planners can be matched to the right vendors for the occasion (mirrors
    // VendorProfile.cateredEventTypes, also editable later in Account
    // Settings via VendorSettingsRequest).
    @NotEmpty(message = "Select at least one event type you cater to")
    private List<EventType> cateredEventTypes;

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
}
