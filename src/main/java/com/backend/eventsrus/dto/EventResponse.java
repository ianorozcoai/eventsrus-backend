package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.EventType;
import java.math.BigDecimal;
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
    private BigDecimal budget;
    private Integer guestCount;
    private String aiIdeaText;
    private boolean saved;
    private List<SuggestedVendorResponse> suggestions;
    private List<ChecklistItemResponse> checklist;
    /** VendorProfile ids this planner has bookmarked for this event - lets a storefront page show its bookmark button already toggled on. */
    private List<Long> bookmarkedVendorProfileIds;
}
