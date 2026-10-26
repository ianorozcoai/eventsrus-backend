package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BillingSource;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** What an admin's "extend access" action did: how long was added and where the supplier's access now ends. */
@Getter
@Builder
@AllArgsConstructor
public class AdminExtendAccessResponse {

    private Long vendorUserId;
    private int daysAdded;
    /** When the supplier's paid/free access now ends (the grace period, if any, comes after this). */
    private Instant accessEndsAt;
    private BillingSource billingSource;
}
