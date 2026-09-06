package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.EventType;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class EventSummaryResponse {

    private Long id;
    private String name;
    private EventType eventType;
    private LocalDate eventDate;
    private boolean saved;
}
