package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.PlanTier;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SubscriptionStatusResponse {

    private PlanTier plan;
    private Instant expiresAt;
    private boolean expiringSoon;
    private boolean expired;
    private boolean inGracePeriod;
    private Instant graceEndsAt;
}
