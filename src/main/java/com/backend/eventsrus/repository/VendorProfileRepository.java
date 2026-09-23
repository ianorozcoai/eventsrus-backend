package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.model.VendorProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorProfileRepository extends JpaRepository<VendorProfile, Long> {

    Optional<VendorProfile> findByUserId(Long userId);

    Optional<VendorProfile> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Optional<VendorProfile> findByReferralCode(String referralCode);

    boolean existsByReferralCode(String referralCode);

    List<VendorProfile> findByCityIgnoreCase(String city);

    /** Vendors who include {@code businessType} among their types, whose operating areas include {@code area}, or who serve "Entire Philippines". */
    @Query("select vp from VendorProfile vp join vp.businessTypes bt join vp.operatingAreas oa "
            + "where bt = :businessType and (oa = :area or oa = 'Entire Philippines')")
    List<VendorProfile> findByBusinessTypeAndOperatingArea(
            @Param("businessType") BusinessType businessType, @Param("area") String area);
}
