package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.Role;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SupportTicketMessageResponse {

    private Long id;
    private Long senderUserId;
    private String senderName;
    private Role senderRole;
    private String body;
    private Instant createdAt;
}
