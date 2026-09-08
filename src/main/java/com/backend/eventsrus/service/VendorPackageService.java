package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.VendorPackageImageResponse;
import com.backend.eventsrus.dto.VendorPackageRequest;
import com.backend.eventsrus.dto.VendorPackageResponse;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorPackage;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorPackageRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VendorPackageService {

    private final VendorPackageRepository vendorPackageRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final VendorPackageImageService vendorPackageImageService;

    @Transactional
    public VendorPackageResponse createPackage(String vendorEmail, VendorPackageRequest request) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorPackage saved = vendorPackageRepository.save(VendorPackage.builder()
                .vendorProfile(profile)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .packageType(request.getPackageType())
                .pricingType(request.getPricingType())
                .minPrice(request.getMinPrice())
                .maxPrice(request.getMaxPrice())
                .active(true)
                .build());
        return toResponse(saved);
    }

    @Transactional
    public VendorPackageResponse updatePackage(String vendorEmail, Long packageId, VendorPackageRequest request) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorPackage pkg = vendorPackageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalStateException("Package not found: " + packageId));
        if (!pkg.getVendorProfile().getId().equals(profile.getId())) {
            throw new IllegalStateException("Package does not belong to the authenticated vendor");
        }
        pkg.setName(request.getName());
        pkg.setDescription(request.getDescription());
        pkg.setPrice(request.getPrice());
        pkg.setPackageType(request.getPackageType());
        pkg.setPricingType(request.getPricingType());
        pkg.setMinPrice(request.getMinPrice());
        pkg.setMaxPrice(request.getMaxPrice());
        vendorPackageRepository.save(pkg);
        return toResponse(pkg);
    }

    @Transactional
    public void deletePackage(String vendorEmail, Long packageId) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorPackage pkg = vendorPackageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalStateException("Package not found: " + packageId));
        if (!pkg.getVendorProfile().getId().equals(profile.getId())) {
            throw new IllegalStateException("Package does not belong to the authenticated vendor");
        }
        vendorPackageRepository.delete(pkg);
    }

    @Transactional
    public VendorPackageResponse setActive(String vendorEmail, Long packageId, boolean active) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorPackage pkg = vendorPackageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalStateException("Package not found: " + packageId));
        if (!pkg.getVendorProfile().getId().equals(profile.getId())) {
            throw new IllegalStateException("Package does not belong to the authenticated vendor");
        }
        pkg.setActive(active);
        vendorPackageRepository.save(pkg);
        return toResponse(pkg);
    }

    @Transactional(readOnly = true)
    public List<VendorPackageResponse> listForVendor(String vendorEmail) {
        VendorProfile profile = requireProfile(vendorEmail);
        List<VendorPackage> packages = vendorPackageRepository.findByVendorProfileIdOrderByCreatedAtDesc(profile.getId());
        // One batched image query for the whole list, not one per package -
        // this used to be the slow part of both loading and (since Add
        // Package redirects straight into this same list) creating a
        // package, and it only gets worse as a vendor adds more packages.
        Map<Long, List<VendorPackageImageResponse>> imagesByPackageId =
                vendorPackageImageService.listForPackages(packages.stream().map(VendorPackage::getId).toList());
        return packages.stream()
                .map(pkg -> toResponse(pkg, imagesByPackageId.getOrDefault(pkg.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasAnyPackages(String vendorEmail) {
        VendorProfile profile = requireProfile(vendorEmail);
        return vendorPackageRepository.existsByVendorProfileId(profile.getId());
    }

    private VendorProfile requireProfile(String vendorEmail) {
        User user = userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + vendorEmail));
        return vendorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + user.getId()));
    }

    /** Single-package callers (create/update/setActive) - one package, one query is fine here. */
    private VendorPackageResponse toResponse(VendorPackage pkg) {
        return toResponse(pkg, vendorPackageImageService.listForPackage(pkg.getId()));
    }

    private VendorPackageResponse toResponse(VendorPackage pkg, List<VendorPackageImageResponse> images) {
        return VendorPackageResponse.builder()
                .id(pkg.getId())
                .name(pkg.getName())
                .description(pkg.getDescription())
                .price(pkg.getPrice())
                .packageType(pkg.getPackageType())
                .pricingType(pkg.getPricingType())
                .minPrice(pkg.getMinPrice())
                .maxPrice(pkg.getMaxPrice())
                .active(pkg.isActive())
                .images(images)
                .build();
    }
}
