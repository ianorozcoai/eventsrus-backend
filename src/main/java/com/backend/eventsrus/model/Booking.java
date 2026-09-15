package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.enums.PaymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Two ways a Booking comes to exist: a vendor cold-proposes a price/date
 * (quotation is null, status flows PROPOSED -> APPROVED/DECLINED) or a
 * planner books off an already-RESPONDED Quotation (quotation is set,
 * status flows AWAITING_PAYMENT -> PAYMENT_SUBMITTED -> BOOKED, with
 * PAYMENT_REJECTED as a detour back to PAYMENT_SUBMITTED) - see
 * BookingService for both paths. CANCELLED applies to either.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_user_id", nullable = false)
    private User vendorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planner_user_id", nullable = false)
    private User plannerUser;

    // Null for a vendor cold-proposal; set when this booking was created by
    // a planner off a RESPONDED Quotation (BookingService#bookFromQuotation).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id")
    private Quotation quotation;

    private BigDecimal price;

    @Column(name = "event_datetime")
    private Instant eventDatetime;

    @Column(name = "agreement_details", columnDefinition = "TEXT")
    private String agreementDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "proposed_at", nullable = false)
    private Instant proposedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    // Payment proof (planner-initiated path only) - private-bucket key,
    // same visibility convention as ID cards; read back via presigned URL.
    @Column(name = "payment_screenshot_key")
    private String paymentScreenshotKey;

    @Column(name = "payment_screenshot_uploaded_at")
    private Instant paymentScreenshotUploadedAt;

    @Column(name = "payment_acknowledged_at")
    private Instant paymentAcknowledgedAt;

    // Set by the vendor on reject; overwritten on each reject, cleared when
    // the planner re-submits a screenshot.
    @Column(name = "payment_rejection_reason", columnDefinition = "TEXT")
    private String paymentRejectionReason;

    // Invoice/receipt the vendor must attach when acknowledging payment -
    // private-bucket key, same visibility convention as the payment
    // screenshot above; read back via presigned URL. Required at
    // acknowledgement time, so this is always set once status is BOOKED.
    @Column(name = "invoice_key")
    private String invoiceKey;

    @Column(name = "invoice_uploaded_at")
    private Instant invoiceUploadedAt;

    // Cancellation - either participant can cancel from any non-terminal
    // status (see BookingService#cancel). A reason is always required, and
    // the transition is recorded in booking_status_history same as every
    // other status change.
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id")
    private User cancelledBy;

    // Only set on a Booking created via QuotationService#acceptBooking (the
    // new quotation-driven flow, Phase 1 of the lifecycle rework) - null for
    // a vendor cold-proposal and for any booking that predates this.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type")
    private PaymentType paymentType;

    // The vendor's confirmation message entered when accepting the booking - same call as paymentType above.
    @Column(name = "confirmation_message", columnDefinition = "TEXT")
    private String confirmationMessage;
}
