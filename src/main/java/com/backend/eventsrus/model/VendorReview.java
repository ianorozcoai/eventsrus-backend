package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A planner's review of one completed booking. Eligibility (successful
 * booking, event 3+ days past, not already reviewed) is enforced by
 * ReviewService before this is created. {@code hidden} lets an admin
 * suppress an abusive review without hard-deleting it.
 */
@Entity
@Table(name = "vendor_reviews")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorReview extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    // Denormalised from booking.vendorUser - never changes, and keeps the
    // storefront's "reviews for this vendor" query a plain lookup.
    @Column(name = "vendor_user_id", nullable = false)
    private Long vendorUserId;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    private boolean hidden;
}
