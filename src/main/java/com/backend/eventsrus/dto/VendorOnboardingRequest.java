package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

    @AssertTrue(message = "You must accept the vendor terms of service")
    private boolean acceptedTerms;
}
