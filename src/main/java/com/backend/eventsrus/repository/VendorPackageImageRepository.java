package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorPackageImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorPackageImageRepository extends JpaRepository<VendorPackageImage, Long> {

    List<VendorPackageImage> findByVendorPackageIdOrderByCreatedAtAsc(Long vendorPackageId);

    /**
     * Every image for several packages at once, in one query - used when
     * building a package LIST (vendor's own Manage Packages, or the
     * storefront) so that doesn't turn into one query per package (N+1).
     * Grouping by package id is the caller's job (see
     * VendorPackageImageService#listForPackages).
     */
    List<VendorPackageImage> findByVendorPackage_IdInOrderByCreatedAtAsc(List<Long> vendorPackageIds);

    /** Every image across every one of a vendor's packages, for the storefront Gallery - see VendorPackageImageService. */
    List<VendorPackageImage> findByVendorPackage_VendorProfile_IdOrderByCreatedAtDesc(Long vendorProfileId);
}
