package com.backend.eventsrus.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorDashboardResponse {

    private long newLeadsCount;
    private long newInquiriesCount;
    private long newQuotationsCount;
    private long newBookingsCount;
    private long bookingsNeedingActionCount;
    private long upcomingEventsCount;
    private BigDecimal totalIncome;
    private long cancellationsCount;
    private boolean hasPackages;
}
