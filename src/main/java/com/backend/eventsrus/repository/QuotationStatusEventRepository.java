package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.QuotationStatus;
import com.backend.eventsrus.model.QuotationStatusEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationStatusEventRepository extends JpaRepository<QuotationStatusEvent, Long> {

    List<QuotationStatusEvent> findByQuotationIdOrderByCreatedAtAsc(Long quotationId);

    /**
     * The actual vendor-sent offer for one negotiation round - a given
     * version number can have several history rows (e.g. QUOTE_SENT then,
     * on acceptance, QUOTE_ACCEPTED and PENDING_DEPOSIT all stamped with
     * that same version - see QuotationService#recordStatusChange), so this
     * narrows to the one row that's actually a sendable quote, for
     * QuotationService#acceptQuote's "accept an earlier version" flow.
     */
    Optional<QuotationStatusEvent> findFirstByQuotationIdAndVersionAndToStatusIn(
            Long quotationId, Integer version, List<QuotationStatus> toStatuses);
}
