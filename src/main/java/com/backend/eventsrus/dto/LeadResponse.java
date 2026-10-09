package com.backend.eventsrus.dto;

import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class LeadResponse {

    private Long id;
    private Long plannerUserId;
    private String plannerName;
    private Long eventId;
    private String eventName;
    /** Null when the planner never set a date for this event - not mandatory. */
    private LocalDate eventDate;
    private Instant firstVisitedAt;
    private Instant lastVisitedAt;
}
