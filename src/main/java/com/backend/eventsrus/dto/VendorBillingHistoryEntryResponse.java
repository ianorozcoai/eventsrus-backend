package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.PlanTier;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorBillingHistoryEntryResponse {

    private Long id;
    private PlanTier plan;
    private BillingSource billingSource;
    private BigDecimal amount;
    private String currency;
    private String paypalTransactionId;
    private Instant periodStart;
    private Instant periodEnd;
    private Instant occurredAt;
}
