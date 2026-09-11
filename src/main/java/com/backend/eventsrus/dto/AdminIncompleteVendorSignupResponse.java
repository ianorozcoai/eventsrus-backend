package com.backend.eventsrus.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * A Google account that signed up through "Become a Vendor" but never
 * finished the onboarding form - no VendorProfile exists for it yet.
 * See UserService#listIncompleteVendorSignupsForAdmin.
 */
@Getter
@Builder
@AllArgsConstructor
public class AdminIncompleteVendorSignupResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String mobileNumber;
    private Instant signedUpAt;
}
