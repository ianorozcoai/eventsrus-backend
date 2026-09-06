package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorPackage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorPackageRepository extends JpaRepository<VendorPackage, Long> {

    List<VendorPackage> findByVendorProfileIdOrderByCreatedAtDesc(Long vendorProfileId);

    boolean existsByVendorProfileId(Long vendorProfileId);
}
