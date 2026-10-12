package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Event extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planner_id", nullable = false)
    private User planner;

    /**
     * Required for every event created since the name/type/date/location
     * intake became mandatory up front (CreateEventRequest) - null only on
     * a legacy event from before that change, created via the old
     * create-then-separately-saveEvent(name) flow.
     */
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "event_date")
    private LocalDate eventDate;

    /**
     * Required at creation (see CreateEventRequest) - null only on a
     * legacy event from before location became mandatory, which falls
     * back to generic vendor-type suggestions rather than matched vendors.
     */
    private String location;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Optional - the planner's own budget figure, PHP. Purely informational for now, not yet used to filter/rank suppliers. */
    private BigDecimal budget;

    /** Optional - expected headcount. */
    @Column(name = "guest_count")
    private Integer guestCount;

    /** AI-generated "how to execute this event" writeup. */
    @Column(name = "ai_idea_text", columnDefinition = "TEXT")
    private String aiIdeaText;

    @Column(nullable = false)
    private boolean saved;
}
