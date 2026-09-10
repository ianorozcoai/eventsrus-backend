package com.backend.eventsrus.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorVerificationDocumentsResponse {

    private Long vendorUserId;
    private String businessName;
    private String ownerName;

    private String idCardUrl;
    private String selfieUrl;
    // Replaces the old single businessPermitUrl - a vendor can have several
    // of these (DTI, SEC, Mayor's Permit, Barangay Clearance, BIR, ...).
    private List<VendorLegalDocumentResponse> legalDocuments;

    // The real, admin-set verification state - see AdminVendorController#verify.
    private boolean verified;
    private Instant verifiedAt;
    private String verifiedByAdmin;

    // Admin-set "Top Vendor" spotlight, independent of verification.
    private boolean topVendor;

    // Every review this vendor has (including hidden ones - the admin view
    // needs to see and un-hide those). Newest first.
    private List<ReviewResponse> reviews;
}
