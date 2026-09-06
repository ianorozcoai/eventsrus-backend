package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Created/touched whenever a planner views a vendor's page in the context of one of their events. */
@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Lead extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_user_id", nullable = false)
    private User vendorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planner_user_id", nullable = false)
    private User plannerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "first_visited_at", nullable = false)
    private Instant firstVisitedAt;

    @Column(name = "last_visited_at", nullable = false)
    private Instant lastVisitedAt;
}
