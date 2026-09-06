package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.NotificationType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private NotificationType type;
    private String title;
    private String body;
    private String relatedEntityType;
    private Long relatedEntityId;
    private boolean read;
    private Instant createdAt;
}
