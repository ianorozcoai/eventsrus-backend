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
}
