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
    // "Unseen since I last opened that page" - see BadgeService. Distinct
    // from newQuotationsCount/bookingsNeedingActionCount above (those are
    // "needs a response", a narrower, standing-status-based count); these
    // back the sidebar nav badges specifically and clear the moment the
    // vendor actually visits Quotations/Bookings, no matter what state
    // things are left in.
    private long quotationsUnseenCount;
    private long bookingsUnseenCount;
}
