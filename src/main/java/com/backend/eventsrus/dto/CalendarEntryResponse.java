package com.backend.eventsrus.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CalendarEntryResponse {

    private Long eventId;
    private String eventName;
    private Instant eventDatetime;

    /** BOOKED or INQUIRY (vendor calendar only — planner entries are always booked). */
    private String status;

    /** Planner calendar: names of vendors booked for this event. */
    private List<String> vendorNames;

    /** Vendor calendar: the planner's name for this event. */
    private String plannerName;
}
