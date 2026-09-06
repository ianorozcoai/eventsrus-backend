package com.backend.eventsrus.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VendorPaymentMethodRequest {

    @NotBlank
    private String label;
}
