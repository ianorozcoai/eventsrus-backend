package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ConversationSummaryResponse {

    private Long id;
    private Long eventId;
    private String eventName;
    private LocalDate eventDate;
    private Long otherPartyUserId;
    private String otherPartyName;
    /** Null when the other party is a planner (not a vendor) - e.g. viewed from a vendor's own Messages page. */
    private BusinessType otherPartyBusinessType;
    private String otherPartySlug;
    private String lastMessagePreview;
    private Instant lastMessageAt;
    private long unreadCount;
}
