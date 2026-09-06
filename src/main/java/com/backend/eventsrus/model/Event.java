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

    /** Null until the planner explicitly saves the event (e.g. "Ian's Birthday"). */
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "event_date")
    private LocalDate eventDate;

    /** Optional — when absent, suggestions are generic vendor types rather than matched vendors. */
    private String location;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** AI-generated "how to execute this event" writeup. */
    @Column(name = "ai_idea_text", columnDefinition = "TEXT")
    private String aiIdeaText;

    @Column(nullable = false)
    private boolean saved;
}
