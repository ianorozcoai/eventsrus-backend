package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BillingCycle;
import com.backend.eventsrus.enums.PlanTier;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateSubscriptionRequest {

    @NotNull
    private PlanTier plan;

    @NotNull
    private BillingCycle billingCycle;
}
