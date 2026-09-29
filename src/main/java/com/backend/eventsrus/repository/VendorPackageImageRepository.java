package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorPackageImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorPackageImageRepository extends JpaRepository<VendorPackageImage, Long> {

    List<VendorPackageImage> findByVendorPackageIdOrderByCreatedAtAsc(Long vendorPackageId);

    long countByVendorPackageId(Long vendorPackageId);

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

    /**
     * Same as above, but only from active (not discontinued) packages - for
     * the vendor's own Gallery Management page (see
     * VendorImageTagService#listAllTaggableImages). A discontinued
     * package's photos are invisible on the public storefront, so showing
     * them here too would let a vendor tag one and see the tag apparently
     * "work" in Gallery while it's actually empty on the storefront - this
     * keeps what Gallery shows consistent with what's ever publicly visible.
     */
    List<VendorPackageImage> findByVendorPackage_VendorProfile_IdAndVendorPackage_ActiveTrueOrderByCreatedAtDesc(Long vendorProfileId);
}
