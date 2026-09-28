package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorImageTag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorImageTagRepository extends JpaRepository<VendorImageTag, Long> {

    List<VendorImageTag> findByVendorProfileIdOrderByNameAsc(Long vendorProfileId);

    Optional<VendorImageTag> findByVendorProfileIdAndNameIgnoreCase(Long vendorProfileId, String name);

    // Silently drops any id that isn't this vendor's own tag - see
    // VendorImageTagService#setGalleryPhotoTags/#setPackageImageTags, which
    // re-resolve every submitted tag id through this before associating.
    List<VendorImageTag> findByIdInAndVendorProfileId(List<Long> ids, Long vendorProfileId);
}
