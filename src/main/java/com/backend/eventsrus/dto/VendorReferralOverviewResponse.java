package com.backend.eventsrus.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorReferralOverviewResponse {

    private String referralCode;
    private String referralLink;
    private BigDecimal totalPendingCommission;
    private BigDecimal totalPaidCommission;
    private List<VendorReferralResponse> referrals;
}
