package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.TicketCategory;
import com.backend.eventsrus.enums.TicketStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SupportTicketResponse {

    private Long id;
    private String subject;
    private TicketCategory category;
    private TicketStatus status;
    private Long raisedByUserId;
    private String raisedByName;
    private String raisedByEmail;
    private Role raisedByRole;
    private Long relatedEventId;
    private String relatedEventName;
    private Long relatedBookingId;
    private Long relatedQuotationId;
    private Long assignedAdminUserId;
    private String assignedAdminName;
    private String lastMessagePreview;
    private Instant lastMessageAt;
    private Instant resolvedAt;
    private Instant closedAt;
    private Instant createdAt;
}
