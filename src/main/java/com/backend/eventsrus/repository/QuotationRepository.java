package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.Quotation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    List<Quotation> findByEventIdOrderByCreatedAtDesc(Long eventId);

    List<Quotation> findByVendorUserIdOrderByCreatedAtDesc(Long vendorUserId);

    List<Quotation> findByPlannerUserIdOrderByCreatedAtDesc(Long plannerUserId);
}
