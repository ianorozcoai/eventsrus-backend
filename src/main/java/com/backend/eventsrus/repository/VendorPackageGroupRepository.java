package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorPackageGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorPackageGroupRepository extends JpaRepository<VendorPackageGroup, Long> {

    List<VendorPackageGroup> findByVendorProfileIdOrderByNameAsc(Long vendorProfileId);

    Optional<VendorPackageGroup> findByVendorProfileIdAndNameIgnoreCase(Long vendorProfileId, String name);

    // Silently drops any id that isn't this vendor's own group - see
    // VendorPackageGroupService#setPackageGroups, which re-resolves every
    // submitted group id through this before associating.
    List<VendorPackageGroup> findByIdInAndVendorProfileId(List<Long> ids, Long vendorProfileId);
}
