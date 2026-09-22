package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.EventType;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "vendor_profiles")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(nullable = false, unique = true)
    private String slug;

    // Vendor referral program - generated once at first onboarding (see
    // UserService#becomeVendor, same lazy-generate-if-null pattern as slug
    // above). Shared with another prospective vendor as
    // {app.frontend-base-url}/vendor/?ref=CODE - see VendorReferralService.
    @Column(name = "referral_code", unique = true)
    private String referralCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_image_url")
    private String logoImageUrl;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "facebook_page_url")
    private String facebookPageUrl;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    private String city;

    private String state;

    @Column(name = "postal_code")
    private String postalCode;

    private String country;

    @Column(name = "id_card_key")
    private String idCardKey;

    @Column(name = "selfie_key")
    private String selfieKey;

    // Manual admin review, not automatic - uploading documents at
    // onboarding used to be enough to show a "Verified Vendor" badge
    // (derived purely from idCardKey/selfieKey being non-null), which meant
    // nobody at EventsRUs ever actually looked at what was submitted. See
    // AdminVendorController for the real review workflow.
    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by_admin")
    private String verifiedByAdmin;

    // Admin-set spotlight flag, independent of `verified` - a vendor can be
    // a Top Vendor without being verified, or vice versa. See
    // AdminVendorController#markTop.
    @Column(name = "top_vendor", nullable = false)
    @Builder.Default
    private boolean topVendor = false;

    // Business-registration paperwork (DTI/SEC/Mayor's Permit/Barangay
    // Clearance/BIR/...) now lives in VendorLegalDocument, a child
    // collection - a business can reasonably have several of these at once,
    // which a single column couldn't represent. See VendorLegalDocumentService.

    @Column(name = "business_name")
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type")
    private BusinessType businessType;

    // Business Scope (Account Settings tab 2)
    @Enumerated(EnumType.STRING)
    @Column(name = "primary_category")
    private BusinessType primaryCategory;

    @Column(name = "max_guest_capacity")
    private Integer maxGuestCapacity;

    // Distinct from maxGuestCapacity above (headcount per event) - this is
    // how many separate client bookings the vendor can take on in one day.
    @Column(name = "max_customers_per_day")
    private Integer maxCustomersPerDay;

    @Column(name = "base_price")
    private BigDecimal basePrice;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "storefront_overview", columnDefinition = "TEXT")
    private String storefrontOverview;

    // Marketplace Escrow & Policies (Account Settings tab 3) - both are
    // vendor-uploaded PDFs in the public S3 bucket, so planners can view
    // them directly; these store the resulting public URL, same convention
    // as logoImageUrl above.
    @Column(name = "cancellation_policy_url")
    private String cancellationPolicyUrl;

    @Column(name = "refund_terms_url")
    private String refundTermsUrl;

    // Payment Methods (Account Settings tab 4) - free-text instructions;
    // the QR codes themselves live in the separate vendor_payment_methods
    // child table (VendorPaymentMethod), one-to-many off this profile.
    @Column(name = "payment_instructions", columnDefinition = "TEXT")
    private String paymentInstructions;

    // Service Scope (Account Settings tab 2) - provinces this vendor
    // serves, or "Entire Philippines" - used by VendorSearchService to
    // match vendors against a planner's event location.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "vendor_operating_areas", joinColumns = @JoinColumn(name = "vendor_profile_id"))
    @Column(name = "area")
    @Builder.Default
    private List<String> operatingAreas = new ArrayList<>();

    // Service Scope (Account Settings tab 2) - which event types this
    // vendor caters to (e.g. a caterer might only do weddings, not
    // corporate parties). A real enum, unlike operatingAreas' free-form
    // province strings, so this is a typed collection.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "vendor_catered_event_types", joinColumns = @JoinColumn(name = "vendor_profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    @Builder.Default
    private List<EventType> cateredEventTypes = new ArrayList<>();
}
