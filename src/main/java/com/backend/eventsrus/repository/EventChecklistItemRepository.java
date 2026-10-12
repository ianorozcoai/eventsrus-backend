package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.model.EventChecklistItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventChecklistItemRepository extends JpaRepository<EventChecklistItem, Long> {

    List<EventChecklistItem> findByEventIdOrderByCreatedAtAsc(Long eventId);

    void deleteByEventIdAndVendorType(Long eventId, BusinessType vendorType);
}
