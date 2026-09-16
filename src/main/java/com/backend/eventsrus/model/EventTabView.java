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

/**
 * One user's "last looked at this event's Quotations/Bookings tab" state -
 * a planner sees these two tabs per event (not one nav-wide list like a
 * vendor's sidebar), so the unseen-activity badge on each tab needs its own
 * per-(user, event) timestamp rather than the single column User#
 * quotationsBadgeSeenAt/bookingsBadgeSeenAt works with on the vendor side.
 * See BadgeService for how both are actually used.
 */
@Entity
@Table(name = "event_tab_views")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class EventTabView extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "quotations_seen_at")
    private Instant quotationsSeenAt;

    @Column(name = "bookings_seen_at")
    private Instant bookingsSeenAt;
}
