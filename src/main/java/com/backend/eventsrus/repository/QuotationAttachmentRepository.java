package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.QuotationAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationAttachmentRepository extends JpaRepository<QuotationAttachment, Long> {

    List<QuotationAttachment> findByQuotationIdOrderByCreatedAtAsc(Long quotationId);
}
