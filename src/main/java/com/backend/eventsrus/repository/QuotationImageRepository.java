package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.QuotationImageSource;
import com.backend.eventsrus.model.QuotationImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationImageRepository extends JpaRepository<QuotationImage, Long> {

    List<QuotationImage> findByQuotationIdAndSourceOrderByCreatedAtAsc(Long quotationId, QuotationImageSource source);

    long countByQuotationIdAndSource(Long quotationId, QuotationImageSource source);
}
