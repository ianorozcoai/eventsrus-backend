package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class BusinessTypeSupplierCountResponse {

    private BusinessType businessType;
    private long count;
}
