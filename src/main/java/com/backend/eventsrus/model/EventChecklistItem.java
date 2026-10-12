package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.ChecklistSource;
import com.backend.eventsrus.enums.ChecklistStatus;
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

@Entity
@Table(name = "event_checklist_items")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class EventChecklistItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChecklistStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChecklistSource source;

    /** Which supplier category this came from, if any - null for a planner-typed custom item or a bookmark-origin item (see vendorProfile below). */
    @Enumerated(EnumType.STRING)
    @Column(name = "vendor_type")
    private BusinessType vendorType;

    /** Which specific vendor this came from, if any - set only for a bookmark-origin item, so the client can link straight to that vendor's storefront. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_profile_id")
    private VendorProfile vendorProfile;
}
