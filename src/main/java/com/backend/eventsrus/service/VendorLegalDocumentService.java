package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.VendorLegalDocumentResponse;
import com.backend.eventsrus.enums.LegalDocumentType;
import com.backend.eventsrus.exception.InvalidFileTypeException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorLegalDocument;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorLegalDocumentRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * A vendor's business-registration paperwork - DTI, SEC, Mayor's Permit,
 * Barangay Clearance, BIR, or anything else (OTHER) - replacing the old
 * single VendorProfile#businessPermitKey column, since a business can
 * reasonably have several of these at once. Unlike VendorPaymentMethod
 * there's no approval workflow: every upload is immediately visible on the
 * storefront (see #listForStorefront) - this is business-legitimacy
 * paperwork, not a financial-risk item like a payment QR code.
 */
@Service
@RequiredArgsConstructor
public class VendorLegalDocumentService {

    // Was 15 minutes - too short for a "View file" link sitting on a
    // settings/storefront page a viewer might leave open a while before
    // clicking it (see UserService's own PRESIGNED_URL_TTL for the same fix).
    private static final Duration PRESIGNED_URL_TTL = Duration.ofHours(1);

    private final VendorLegalDocumentRepository vendorLegalDocumentRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final S3UploadService s3UploadService;

    @Transactional
    public VendorLegalDocumentResponse create(
            String vendorEmail, LegalDocumentType documentType, String label, MultipartFile file) {
        return toResponse(createInternal(requireProfile(vendorEmail), documentType, label, file));
    }

    /** For UserService#becomeVendor, which already has the VendorProfile loaded mid-onboarding. */
    @Transactional
    public VendorLegalDocument createForProfile(
            VendorProfile profile, LegalDocumentType documentType, String label, MultipartFile file) {
        return createInternal(profile, documentType, label, file);
    }

    private VendorLegalDocument createInternal(
            VendorProfile profile, LegalDocumentType documentType, String label, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileTypeException("A document file is required");
        }
        String key = s3UploadService
                .upload(file, keyPrefix(profile.getUser().getId()), S3UploadService.Visibility.PRIVATE)
                .key();
        return vendorLegalDocumentRepository.save(VendorLegalDocument.builder()
                .vendorProfile(profile)
                .documentType(documentType)
                .label(label)
                .fileKey(key)
                .build());
    }

    @Transactional
    public void delete(String vendorEmail, Long documentId) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorLegalDocument document = vendorLegalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Document not found: " + documentId));
        if (!document.getVendorProfile().getId().equals(profile.getId())) {
            throw new IllegalStateException("Document does not belong to the authenticated vendor");
        }
        vendorLegalDocumentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public List<VendorLegalDocumentResponse> listForVendor(String vendorEmail) {
        VendorProfile profile = requireProfile(vendorEmail);
        return listForProfile(profile.getId());
    }

    /** Every document is shown - no approval gate, see class Javadoc. */
    @Transactional(readOnly = true)
    public List<VendorLegalDocumentResponse> listForStorefront(Long vendorProfileId) {
        return listForProfile(vendorProfileId);
    }

    private List<VendorLegalDocumentResponse> listForProfile(Long vendorProfileId) {
        return vendorLegalDocumentRepository.findByVendorProfileIdOrderByCreatedAtDesc(vendorProfileId).stream()
                .map(this::toResponse)
                .toList();
    }

    private VendorProfile requireProfile(String vendorEmail) {
        User user = userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + vendorEmail));
        return vendorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + user.getId()));
    }

    private VendorLegalDocumentResponse toResponse(VendorLegalDocument document) {
        return VendorLegalDocumentResponse.builder()
                .id(document.getId())
                .documentType(document.getDocumentType())
                .label(document.getLabel())
                .url(s3UploadService.presignedUrl(document.getFileKey(), PRESIGNED_URL_TTL))
                .createdAt(document.getCreatedAt())
                .build();
    }

    private String keyPrefix(Long userId) {
        return "vendors/" + userId + "/legal-document";
    }
}
