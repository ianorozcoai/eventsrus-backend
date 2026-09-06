package com.backend.eventsrus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CreateSubscriptionResponse {

    private Long vendorSubscriptionId;
    private String approvalUrl;
}
