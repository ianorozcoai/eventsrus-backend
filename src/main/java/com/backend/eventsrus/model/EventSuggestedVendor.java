package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.BusinessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A single AI-suggested vendor slot for an event: either a real matched
 * {@link VendorProfile} (location known) or just a suggested category
 * (location unknown — vendorProfile stays null).
 */
@Entity
@Table(name = "event_suggested_vendors")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class EventSuggestedVendor extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_profile_id")
    private VendorProfile vendorProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "vendor_type", nullable = false)
    private BusinessType vendorType;

    /** Whether the planner has opted into actively browsing/pursuing this category - defaults false, an explicit choice. */
    @Column(nullable = false)
    @Builder.Default
    private boolean selected = false;

    /** Optional - how much of the event's overall budget the planner earmarks for this one category. */
    private BigDecimal budget;
}
