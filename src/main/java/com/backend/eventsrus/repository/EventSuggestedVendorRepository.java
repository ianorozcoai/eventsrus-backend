package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.model.EventSuggestedVendor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSuggestedVendorRepository extends JpaRepository<EventSuggestedVendor, Long> {

    List<EventSuggestedVendor> findByEventId(Long eventId);

    Optional<EventSuggestedVendor> findByEventIdAndVendorType(Long eventId, BusinessType vendorType);

    void deleteByEventId(Long eventId);

    /**
     * Account deletion: a deleted supplier's name is removed from planners' event suggestions, but the row
     * (its category, budget and selection) stays as a category-only suggestion (vendorProfile null).
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update EventSuggestedVendor s set s.vendorProfile = null where s.vendorProfile.id = :vendorProfileId")
    int clearVendorProfile(@org.springframework.data.repository.query.Param("vendorProfileId") Long vendorProfileId);
}
