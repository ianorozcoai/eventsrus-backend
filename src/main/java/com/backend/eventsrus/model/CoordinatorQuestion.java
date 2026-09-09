package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import jakarta.persistence.Column;
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
 * One question a planner asked the Events Coordinator (the AI ideas/advice
 * assistant, see CoordinatorService) about a specific event, plus the
 * answer given. This table doubles as the usage log the daily question
 * quota is computed from - there's no separate counter to keep in sync.
 */
@Entity
@Table(name = "coordinator_questions")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CoordinatorQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planner_id", nullable = false)
    private User planner;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String answer;
}
