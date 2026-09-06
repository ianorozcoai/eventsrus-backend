package com.backend.eventsrus.dto;

import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ConversationMessageResponse {

    private Long id;
    private Long senderUserId;
    private String senderName;
    private LocalDate targetDate;
    private String body;
    private Instant createdAt;
}
