package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A specific supplier a planner has bookmarked for an event, from that
 * supplier's storefront - distinct from {@link EventSuggestedVendor}, which
 * tracks category-level selection, not a specific vendor. One row per
 * (event, vendor) - see the unique constraint in V66.
 */
@Entity
@Table(name = "event_bookmarked_vendors")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class EventBookmarkedVendor extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendorProfile;
}
