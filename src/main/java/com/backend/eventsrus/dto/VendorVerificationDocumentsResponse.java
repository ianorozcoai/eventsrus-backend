package com.backend.eventsrus.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorVerificationDocumentsResponse {

    private String idCardUrl;
    private String selfieUrl;
    // Replaces the old single businessPermitUrl - a vendor can have several
    // of these (DTI, SEC, Mayor's Permit, Barangay Clearance, BIR, ...).
    private List<VendorLegalDocumentResponse> legalDocuments;
}
