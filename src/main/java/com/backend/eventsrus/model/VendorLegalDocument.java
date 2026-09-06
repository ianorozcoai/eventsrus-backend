package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.LegalDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * One business-registration document a vendor has on file - a business can
 * have several (DTI, SEC, Mayor's Permit, Barangay Clearance, BIR...), so
 * this replaced the old single VendorProfile#businessPermitKey column
 * (V23). Unlike VendorPaymentMethod, there's no PENDING/APPROVED review
 * status - these are business-legitimacy paperwork, not a financial risk
 * like payment QR codes, so every uploaded document is shown on the
 * storefront as soon as it's uploaded (see VendorLegalDocumentService).
 */
@Entity
@Table(name = "vendor_legal_documents")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorLegalDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendorProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private LegalDocumentType documentType;

    /** Free-text display name - mainly useful for OTHER, e.g. "Fire Safety Certificate". */
    private String label;

    @Column(name = "file_key", nullable = false)
    private String fileKey;
}
