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

/** One chat thread per (event, vendor) pair — created on the planner's first inquiry. */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Conversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_user_id", nullable = false)
    private User vendorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planner_user_id", nullable = false)
    private User plannerUser;
}
