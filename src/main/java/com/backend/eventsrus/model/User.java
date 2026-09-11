package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SignupIntent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

    @Column(name = "google_id", nullable = false, unique = true)
    private String googleId;

    @Column
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Which door this account first walked through (planner login vs.
    // vendor sign-up) - set once at creation, never changed afterward. This
    // is the actual identity lock: role still flows PLANNER -> VENDOR at
    // becomeVendor time, but signupIntent is what UserService checks a
    // returning login's declared intent against. See
    // UserService#findOrCreateFromGoogle.
    @Enumerated(EnumType.STRING)
    @Column(name = "signup_intent", nullable = false)
    private SignupIntent signupIntent;

    // Vendor Terms & Agreement acceptance - set once, at becomeVendor time.
    // Null for planners and for any vendor who onboarded before this existed.
    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "terms_version")
    private String termsVersion;

    // Planner profile (Account dropdown -> Profile) - optional address,
    // same shape as VendorProfile's own address fields.
    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    private String city;

    private String state;

    @Column(name = "postal_code")
    private String postalCode;
}
