package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.QuotationStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Append-only audit trail for a Quotation's status changes - same idea as
 * BookingStatusEvent. Doubles as the real version ledger for the
 * negotiation loop: {@code version}/{@code quotedAmount}/{@code targetDate}/
 * {@code packageIds} snapshot exactly what was being asked or offered at
 * that point, since the live {@code Quotation} row only ever holds the
 * latest ask/response (see QuotationService#requestRevision/#respondWithPdf).
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

    // The PDF the vendor sent at exactly this QUOTE_SENT/REVISION_SENT
    // transition - null for every other transition. Lets an old version
    // stay reachable even after a later response supersedes it on the
    // quotation itself (Quotation#pdfKey only ever holds the latest).
    @Column(name = "pdf_key")
    private String pdfKey;

    // Which negotiation round this transition belongs to - see Quotation#version.
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    // Only set on a QUOTE_SENT/REVISION_SENT/BOOKED row - the vendor's total at exactly this version.
    @Column(name = "quoted_amount")
    private BigDecimal quotedAmount;

    // Only set on a REQUEST_FOR_QUOTE/REVISION_REQUESTED row - the target
    // date asked for at exactly this version (Quotation#targetDate only
    // ever holds the latest ask).
    @Column(name = "target_date")
    private LocalDate targetDate;

    // Which packages were being discussed at exactly this version - same
    // reasoning as targetDate/quotedAmount above.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "quotation_status_history_packages", joinColumns = @JoinColumn(name = "history_id"))
    @Column(name = "vendor_package_id")
    @Builder.Default
    private List<Long> packageIds = new ArrayList<>();
}
