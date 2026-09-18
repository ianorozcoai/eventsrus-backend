package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorGalleryPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorGalleryPhotoRepository extends JpaRepository<VendorGalleryPhoto, Long> {

    List<VendorGalleryPhoto> findByVendorProfileIdOrderByCreatedAtDesc(Long vendorProfileId);

    long countByVendorProfileId(Long vendorProfileId);
}
