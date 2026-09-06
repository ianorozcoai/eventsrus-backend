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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "quotations")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Quotation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_user_id", nullable = false)
    private User vendorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planner_user_id", nullable = false)
    private User plannerUser;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "request_message", columnDefinition = "TEXT")
    private String requestMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuotationStatus status;

    /** Private-bucket S3 key for the vendor's uploaded PDF quotation — retrieved via presigned URL. */
    @Column(name = "pdf_key")
    private String pdfKey;

    @Column(name = "responded_at")
    private Instant respondedAt;

    // Set when the planner formally closes this out without booking - see
    // QuotationService#declineQuotation. Cleared (along with pdfKey/
    // respondedAt) if the planner instead sends the quotation back for a
    // revision via #requestRevision, which re-opens it to REQUESTED.
    @Column(name = "declined_at")
    private Instant declinedAt;

    // Which of the vendor's packages this quotation request is about - a
    // planner can flag more than one (same multi-select concept as
    // VendorProfile#operatingAreas/#cateredEventTypes above).
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "quotation_packages", joinColumns = @JoinColumn(name = "quotation_id"))
    @Column(name = "vendor_package_id")
    @Builder.Default
    private List<Long> packageIds = new ArrayList<>();
}
