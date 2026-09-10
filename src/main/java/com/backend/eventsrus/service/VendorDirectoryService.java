package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.VendorPackageImageResponse;
import com.backend.eventsrus.dto.VendorPackageResponse;
import com.backend.eventsrus.dto.VendorPublicProfileResponse;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.VendorPackageRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final VendorPackageImageService vendorPackageImageService;
    private final ReviewService reviewService;

    @Transactional(readOnly = true)
    public VendorPublicProfileResponse getPublicProfile(String slug) {
        VendorProfile profile = vendorProfileRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("Vendor not found: " + slug));
        Long vendorUserId = profile.getUser().getId();
        ReviewService.RatingSummary ratings = reviewService.ratingSummary(vendorUserId);

        var activePackages = vendorPackageRepository.findByVendorProfileIdOrderByCreatedAtDesc(profile.getId()).stream()
                .filter(pkg -> pkg.isActive())
                .toList();

        // One batched image query for every active package, not one per
        // package (N+1) - also reused below for galleryImages instead of a
        // second, separate query, since the gallery is just those same
        // photos combined into one list.
        Map<Long, List<VendorPackageImageResponse>> imagesByPackageId = vendorPackageImageService
                .listForPackages(activePackages.stream().map(p -> p.getId()).toList());

        var packages = activePackages.stream()
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
                        .images(imagesByPackageId.getOrDefault(p.getId(), List.of()))
                        .build())
                .toList();

        // Newest first, same ordering listAllForStorefront used to give -
        // an inactive/hidden package's photos deliberately don't show up
        // here either, matching packages[] above.
        var galleryImages = imagesByPackageId.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(VendorPackageImageResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
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
                // This is profile.isVerified() - a real admin-set flag, NOT
                // "has the vendor uploaded an ID card and selfie". Uploading
                // documents just makes a vendor eligible for review; an
                // admin still has to actually look at them and mark the
                // vendor verified (see AdminVendorController#verify) before
                // the storefront's "Verified Vendor" badge appears.
                .identityVerified(profile.isVerified())
                .galleryImages(galleryImages)
                .reviews(reviewService.listPublic(vendorUserId))
                .averageRating(ratings.averageRating())
                .reviewCount(ratings.reviewCount())
                .build();
    }
}
