package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.EventSuggestedVendor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSuggestedVendorRepository extends JpaRepository<EventSuggestedVendor, Long> {

    List<EventSuggestedVendor> findByEventId(Long eventId);

    void deleteByEventId(Long eventId);
}
