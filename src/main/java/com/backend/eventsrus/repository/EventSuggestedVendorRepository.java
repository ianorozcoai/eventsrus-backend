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
}
