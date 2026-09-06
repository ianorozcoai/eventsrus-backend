package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.PaymentMethodStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorPaymentMethodResponse {

    private Long id;
    private String label;
    private String qrImageUrl;
    private PaymentMethodStatus status;
}
