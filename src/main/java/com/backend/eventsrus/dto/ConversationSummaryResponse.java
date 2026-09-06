package com.backend.eventsrus.dto;

import java.time.Instant;
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
    private Long otherPartyUserId;
    private String otherPartyName;
    private String lastMessagePreview;
    private Instant lastMessageAt;
    private long unreadCount;
}
