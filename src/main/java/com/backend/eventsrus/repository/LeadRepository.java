package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.Lead;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    Optional<Lead> findByVendorUserIdAndPlannerUserIdAndEventId(Long vendorUserId, Long plannerUserId, Long eventId);

    List<Lead> findByVendorUserIdOrderByLastVisitedAtDesc(Long vendorUserId);
}
