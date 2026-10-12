package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.EventBookmarkedVendor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventBookmarkedVendorRepository extends JpaRepository<EventBookmarkedVendor, Long> {

    List<EventBookmarkedVendor> findByEventId(Long eventId);

    Optional<EventBookmarkedVendor> findByEventIdAndVendorProfileId(Long eventId, Long vendorProfileId);

    void deleteByEventIdAndVendorProfileId(Long eventId, Long vendorProfileId);
}
