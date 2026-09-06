package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.QuotationStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class QuotationStatusEventResponse {

    private Long id;
    private QuotationStatus fromStatus;
    private QuotationStatus toStatus;
    private Long changedByUserId;
    private String changedByName;
    private String reason;
    private Instant createdAt;
}
