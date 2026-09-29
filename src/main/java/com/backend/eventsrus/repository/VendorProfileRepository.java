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

    /**
     * Real (non-fake-account) vendor count per business type, for the admin
     * Dashboard's breakdown - see AdminDashboardController. A vendor with
     * several business types is counted once per type, not once overall
     * (matches how BusinessType filtering already works everywhere else in
     * the app - a multi-type vendor genuinely belongs in each bucket). Only
     * returns rows for types at least one real vendor actually has -
     * AdminDashboardController fills in zero for every other BusinessType.
     */
    @Query("select bt, count(vp) from VendorProfile vp join vp.businessTypes bt "
            + "where vp.user.fakeAccount = false group by bt")
    List<Object[]> countRealVendorsByBusinessType();
}
