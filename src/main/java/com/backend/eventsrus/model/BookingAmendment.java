package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.AmendmentStatus;
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
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A proposed change to an already-BOOKED Booking's price/date/details/
 * packages - a formal change order rather than letting either side silently
 * overwrite Booking's fields once both parties have already confirmed it
 * (invoice included). Only one PENDING amendment is allowed per booking at
 * a time (see BookingAmendmentService). Every field here is nullable - null
 * means "no change proposed to this field", so a vendor can e.g. propose
 * only a new price without having to restate the unchanged date/details.
 * On acceptance, non-null fields are copied onto the Booking and the event
 * is recorded in booking_status_history (see BookingAmendmentService#respond).
 */
@Entity
@Table(name = "booking_amendments")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class BookingAmendment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_by_user_id", nullable = false)
    private User proposedBy;

    @Column(name = "new_price")
    private BigDecimal newPrice;

    @Column(name = "new_event_datetime")
    private Instant newEventDatetime;

    @Column(name = "new_agreement_details", columnDefinition = "TEXT")
    private String newAgreementDetails;

    // Empty list is treated the same as null (no package changes proposed) -
    // see BookingAmendmentService.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "booking_amendment_packages", joinColumns = @JoinColumn(name = "booking_amendment_id"))
    @Column(name = "vendor_package_id")
    @Builder.Default
    private List<Long> newPackageIds = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AmendmentStatus status;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
