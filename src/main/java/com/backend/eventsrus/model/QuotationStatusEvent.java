package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.QuotationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Append-only audit trail for a Quotation's status changes - same idea as
 * BookingStatusEvent. Notably captures the full text of every revision
 * request in {@code reason} (see QuotationService#requestRevision), since
 * {@code Quotation#requestMessage} itself only ever holds the latest ask.
 */
@Entity
@Table(name = "quotation_status_history")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class QuotationStatusEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private QuotationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private QuotationStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private User changedBy;

    @Column(columnDefinition = "TEXT")
    private String reason;

    // The PDF the vendor sent at exactly this RESPONDED transition - null
    // for every other transition (REQUESTED, DECLINED). Lets an old
    // version stay reachable even after a later response supersedes it on
    // the quotation itself (Quotation#pdfKey only ever holds the latest).
    @Column(name = "pdf_key")
    private String pdfKey;
}
