package com.backend.eventsrus.dto;

import java.time.Instant;
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
    private Instant firstVisitedAt;
    private Instant lastVisitedAt;
}
