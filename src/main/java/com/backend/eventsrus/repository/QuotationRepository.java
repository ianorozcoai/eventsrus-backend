package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.Quotation;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    List<Quotation> findByEventIdOrderByCreatedAtDesc(Long eventId);

    List<Quotation> findByVendorUserIdOrderByCreatedAtDesc(Long vendorUserId);

    List<Quotation> findByPlannerUserIdOrderByCreatedAtDesc(Long plannerUserId);

    boolean existsByEventIdAndVendorUserId(Long eventId, Long vendorUserId);

    // Powers the vendor nav / planner event-tab "unseen" badges (see
    // BadgeService) - anything touched since the viewer's last visit,
    // regardless of which side caused the change.
    long countByVendorUserIdAndUpdatedAtAfter(Long vendorUserId, Instant after);

    long countByEventIdAndUpdatedAtAfter(Long eventId, Instant after);
}
