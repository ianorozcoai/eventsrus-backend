package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.EventType;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class EventResponse {

    private Long id;
    private String name;
    private EventType eventType;
    private LocalDate eventDate;
    private String location;
    private String description;
    private String aiIdeaText;
    private boolean saved;
    private List<SuggestedVendorResponse> suggestions;
    private List<ChecklistItemResponse> checklist;
}
