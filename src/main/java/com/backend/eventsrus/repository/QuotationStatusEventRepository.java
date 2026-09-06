package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.QuotationStatusEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationStatusEventRepository extends JpaRepository<QuotationStatusEvent, Long> {

    List<QuotationStatusEvent> findByQuotationIdOrderByCreatedAtAsc(Long quotationId);
}
