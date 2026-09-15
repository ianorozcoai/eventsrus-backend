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

    // Incremented on every negotiation step (a new ask or a new response) -
    // see QuotationService#recordStatusChange. This is a domain-visible
    // negotiation-round counter shown to both sides ("v3"), NOT a JPA
    // optimistic-lock @Version - unrelated to concurrency control.
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    // The vendor's total for the current version - a flat number, not
    // itemized line items (see the project's quotation-booking-target-
    // state-machine memory for why that's out of scope for now).
    @Column(name = "quoted_amount")
    private BigDecimal quotedAmount;

    // Set when the planner accepts a QUOTE_SENT/REVISION_SENT quote - see
    // QuotationService#acceptQuote. From this point the quote is frozen (no
    // more revisions/decline) per the Booking Conversion rule.
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    // Deposit/payment proof - same private-bucket-key + presigned-URL
    // convention as Booking#paymentScreenshotKey, just living on the
    // Quotation now since payment review happens before a Booking exists.
    @Column(name = "payment_screenshot_key")
    private String paymentScreenshotKey;

    @Column(name = "payment_screenshot_uploaded_at")
    private Instant paymentScreenshotUploadedAt;

    // Set by the vendor on reject; cleared when the planner resubmits - see
    // QuotationService#rejectPaymentScreenshot/#submitPaymentScreenshot.
    @Column(name = "payment_rejection_reason", columnDefinition = "TEXT")
    private String paymentRejectionReason;
}
