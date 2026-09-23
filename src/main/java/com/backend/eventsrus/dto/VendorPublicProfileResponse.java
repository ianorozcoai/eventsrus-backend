package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorPublicProfileResponse {

    private Long vendorUserId;
    private String businessName;
    private String ownerName;
    private String description;
    private String logoImageUrl;
    private List<BusinessType> businessTypes;
    private String city;
    private String state;
    private String country;
    private String contactEmail;
    private String phoneNumber;
    private String facebookPageUrl;
    private List<VendorPackageResponse> packages;
    private String paymentInstructions;
    private List<VendorPaymentMethodResponse> paymentMethods;

    // Every business-registration document the vendor has on file (DTI,
    // SEC, Mayor's Permit, Barangay Clearance, BIR, ...) - a business can
    // have several, so this is a list, not a single flag/URL like it used
    // to be. Each entry's url is a real (short-lived, presigned) link to
    // the actual scanned document - this paperwork is safe to show planners
    // directly. There is deliberately no equivalent for the ID card/selfie -
    // those are personal identity documents; identityVerified is a yes/no
    // summary only, the images themselves stay admin-only (see
    // AdminVendorController#getVerificationDocuments).
    private List<VendorLegalDocumentResponse> legalDocuments;
    private boolean identityVerified;

    // Every photo across every package this vendor has, newest first - see
    // VendorPackageImageService#listAllForStorefront. Each package's own
    // photos are also available nested under packages[].images if a caller
    // wants them grouped instead of flat.
    private List<VendorPackageImageResponse> galleryImages;

    // Real planner reviews (hidden ones excluded), newest first, plus the
    // aggregate. averageRating is null when there are no reviews yet.
    private List<ReviewResponse> reviews;
    private Double averageRating;
    private int reviewCount;
}
