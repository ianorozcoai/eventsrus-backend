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
import lombok.Builder;
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

    // When this user (a vendor, in practice) last looked at their
    // Quotations/Bookings nav pages - see BadgeService. Null means "never",
    // so everything counts as unseen until their first visit.
    @Column(name = "quotations_badge_seen_at")
    private Instant quotationsBadgeSeenAt;

    @Column(name = "bookings_badge_seen_at")
    private Instant bookingsBadgeSeenAt;

    // Set on every successful Google sign-in (see
    // UserService#findOrCreateFromGoogle) - null means never logged in since
    // this column was added (e.g. a seeded/fake account).
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    // True only for accounts the team created directly (e.g. demo vendors
    // seeded to populate the marketplace before real vendors sign up) -
    // never set by any real signup path. Lets these be found and removed in
    // bulk later without guessing from email/name patterns.
    @Column(name = "fake_account", nullable = false)
    @Builder.Default
    private boolean fakeAccount = false;
}
