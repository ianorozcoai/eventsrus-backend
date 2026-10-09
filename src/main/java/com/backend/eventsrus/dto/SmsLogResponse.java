package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.SmsTriggerType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SmsLogResponse {

    private Long id;
    private String recipientNumber;
    private String message;
    private SmsTriggerType triggerType;
    private boolean success;
    private String errorMessage;
    private String sentByAdminEmail;
    private Instant createdAt;
}
