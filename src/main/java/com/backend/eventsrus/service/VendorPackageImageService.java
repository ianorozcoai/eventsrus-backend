package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.VendorPackageImageResponse;
import com.backend.eventsrus.exception.InvalidFileTypeException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorPackage;
import com.backend.eventsrus.model.VendorPackageImage;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorPackageImageRepository;
import com.backend.eventsrus.repository.VendorPackageRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Real photos per package - shown in the vendor's own Manage Packages (edit
 * mode) and, combined across every package a vendor has, as the
 * storefront's Gallery section (see #listAllForStorefront,
 * VendorDirectoryService#getPublicProfile). Public bucket, direct URL - no
 * approval workflow, same reasoning as VendorLegalDocumentService: these
 * are the vendor's own marketing photos, not a financial-risk item like a
 * payment QR code.
 */
@Service
@RequiredArgsConstructor
public class VendorPackageImageService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg");

    private final VendorPackageImageRepository vendorPackageImageRepository;
    private final VendorPackageRepository vendorPackageRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final S3UploadService s3UploadService;

    @Transactional
    public VendorPackageImageResponse create(String vendorEmail, Long packageId, MultipartFile image, String caption) {
        if (image == null || image.isEmpty()) {
            throw new InvalidFileTypeException("An image file is required");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(image.getContentType())) {
            throw new InvalidFileTypeException("Only PNG or JPEG images are accepted for package photos");
        }
        VendorPackage pkg = requireOwnedPackage(vendorEmail, packageId);
        String imageUrl = s3UploadService
                .upload(image, keyPrefix(pkg.getVendorProfile().getUser().getId(), packageId), S3UploadService.Visibility.PUBLIC)
                .url();

        VendorPackageImage saved = vendorPackageImageRepository.save(VendorPackageImage.builder()
                .vendorPackage(pkg)
                .imageUrl(imageUrl)
                .caption(caption)
                .build());
        return toResponse(saved);
    }

    @Transactional
    public void delete(String vendorEmail, Long packageId, Long imageId) {
        requireOwnedPackage(vendorEmail, packageId);
        VendorPackageImage image = vendorPackageImageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalStateException("Package image not found: " + imageId));
        if (!image.getVendorPackage().getId().equals(packageId)) {
            throw new IllegalStateException("Image does not belong to the given package");
        }
        vendorPackageImageRepository.delete(image);
    }

    @Transactional(readOnly = true)
    public List<VendorPackageImageResponse> listForPackage(Long packageId) {
        return vendorPackageImageRepository.findByVendorPackageIdOrderByCreatedAtAsc(packageId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Every image for several packages at once, grouped by package id - one
     * query instead of one-per-package (see #listForPackage, which N+1's
     * badly the moment a list has more than a couple packages). A package
     * with no images just isn't a key in the result - callers should treat
     * a missing key the same as an empty list.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<VendorPackageImageResponse>> listForPackages(List<Long> packageIds) {
        if (packageIds.isEmpty()) {
            return Map.of();
        }
        return vendorPackageImageRepository.findByVendorPackage_IdInOrderByCreatedAtAsc(packageIds).stream()
                .collect(Collectors.groupingBy(
                        image -> image.getVendorPackage().getId(),
                        Collectors.mapping(this::toResponse, Collectors.toList())));
    }

    /** Every image across every package a vendor has, newest first - the storefront's Gallery section. */
    @Transactional(readOnly = true)
    public List<VendorPackageImageResponse> listAllForStorefront(Long vendorProfileId) {
        return vendorPackageImageRepository.findByVendorPackage_VendorProfile_IdOrderByCreatedAtDesc(vendorProfileId).stream()
                .map(this::toResponse)
                .toList();
    }

    private VendorPackage requireOwnedPackage(String vendorEmail, Long packageId) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorPackage pkg = vendorPackageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalStateException("Package not found: " + packageId));
        if (!pkg.getVendorProfile().getId().equals(profile.getId())) {
            throw new IllegalStateException("Package does not belong to the authenticated vendor");
        }
        return pkg;
    }

    private VendorProfile requireProfile(String vendorEmail) {
        User user = userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + vendorEmail));
        return vendorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + user.getId()));
    }

    private VendorPackageImageResponse toResponse(VendorPackageImage image) {
        return VendorPackageImageResponse.builder()
                .id(image.getId())
                .imageUrl(image.getImageUrl())
                .caption(image.getCaption())
                .createdAt(image.getCreatedAt())
                .build();
    }

    private String keyPrefix(Long userId, Long packageId) {
        return "vendors/" + userId + "/packages/" + packageId + "/image";
    }
}
