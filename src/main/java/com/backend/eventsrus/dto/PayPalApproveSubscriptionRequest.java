package com.backend.eventsrus.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The subscription ID PayPal's JS SDK returns client-side once a vendor approves via Smart Buttons. */
@Getter
@Setter
@NoArgsConstructor
public class PayPalApproveSubscriptionRequest {

    @NotBlank
    private String paypalSubscriptionId;
}
