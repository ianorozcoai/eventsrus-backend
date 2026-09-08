package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.ReferralStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorReferralResponse {

    private Long id;
    private String referredBusinessName;
    private ReferralStatus status;
    private BigDecimal commissionAmount;
    private Instant createdAt;
    private Instant convertedAt;
    private Instant paidAt;
}
