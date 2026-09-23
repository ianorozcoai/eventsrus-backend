package com.backend.eventsrus.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** One entry in a vendor's GCash review timeline - see AdminSubscriptionPaymentResponse#history. */
@Getter
@Builder
@AllArgsConstructor
public class GcashHistoryEntryResponse {

    private String eventType;
    private String note;
    private Instant occurredAt;
}
