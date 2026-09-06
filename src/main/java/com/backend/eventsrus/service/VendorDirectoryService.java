package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.VendorPackageResponse;
import com.backend.eventsrus.dto.VendorPublicProfileResponse;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.VendorPackageRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VendorDirectoryService {

    private final VendorProfileRepository vendorProfileRepository;
    private final VendorPackageRepository vendorPackageRepository;
    private final VendorPaymentMethodService vendorPaymentMethodService;
    private final VendorLegalDocumentService vendorLegalDocumentService;

    @Transactional(readOnly = true)
    public VendorPublicProfileResponse getPublicProfile(String slug) {
        VendorProfile profile = vendorProfileRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("Vendor not found: " + slug));

        var packages = vendorPackageRepository.findByVendorProfileIdOrderByCreatedAtDesc(profile.getId()).stream()
                .filter(pkg -> pkg.isActive())
                .map(p -> VendorPackageResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .price(p.getPrice())
                        .packageType(p.getPackageType())
                        .pricingType(p.getPricingType())
                        .minPrice(p.getMinPrice())
                        .maxPrice(p.getMaxPrice())
                        .active(p.isActive())
                        .build())
                .toList();

        return VendorPublicProfileResponse.builder()
                .vendorUserId(profile.getUser().getId())
                .businessName(profile.getBusinessName())
                .ownerName(profile.getOwnerName())
                .description(profile.getDescription())
                .logoImageUrl(profile.getLogoImageUrl())
                .businessType(profile.getBusinessType())
                .city(profile.getCity())
                .state(profile.getState())
                .country(profile.getCountry())
                .contactEmail(profile.getContactEmail())
                .phoneNumber(profile.getPhoneNumber())
                .packages(packages)
                .paymentInstructions(profile.getPaymentInstructions())
                .paymentMethods(vendorPaymentMethodService.listApprovedForStorefront(profile.getId()))
                // Every uploaded business-registration document (DTI, SEC,
                // Mayor's Permit, Barangay Clearance, BIR, ...) - safe to
                // show planners directly, same as the old single businessPermitUrl.
                .legalDocuments(vendorLegalDocumentService.listForStorefront(profile.getId()))
                // Deliberately no idCardUrl/selfieUrl here - those are the
                // vendor's personal identity documents (government ID +
                // face photo), not business-registration paperwork, and
                // showing them to anonymous storefront visitors would be a
                // real privacy/security exposure. Only a verified/not-yet
                // boolean is public; the actual images stay admin-only
                // (AdminVendorController#getVerificationDocuments).
                .identityVerified(profile.getIdCardKey() != null && profile.getSelfieKey() != null)
                .build();
    }
}
