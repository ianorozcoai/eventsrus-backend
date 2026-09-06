package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.BookingStatus;
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
 * Append-only audit trail for a Booking's status changes - fixes the
 * "overwritten rejection reason" problem where {@code Booking#
 * paymentRejectionReason} only ever holds the latest reason. Every
 * transition in BookingService (propose, respond, bookFromQuotation,
 * submitPaymentScreenshot, acknowledgePayment, rejectPayment, cancel) writes
 * one row here via BookingService#recordStatusChange. A row's fromStatus is
 * null for the booking's initial creation event, and its fromStatus/toStatus
 * can be equal for a non-status-changing event worth recording anyway (e.g.
 * an accepted BookingAmendment, which doesn't move status off BOOKED).
 */
@Entity
@Table(name = "booking_status_history")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class BookingStatusEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private BookingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private BookingStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private User changedBy;

    @Column(columnDefinition = "TEXT")
    private String reason;
}
