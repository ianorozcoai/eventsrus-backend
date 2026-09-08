package com.backend.eventsrus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Real counts for the admin module's Dashboard - see AdminDashboardController. */
@Getter
@Builder
@AllArgsConstructor
public class AdminDashboardResponse {

    private long plannerCount;
    private long vendorCount;
    private long vendorTicketCount;
    private long newVendorTicketCount;
    private long newPlannerTicketCount;
}
