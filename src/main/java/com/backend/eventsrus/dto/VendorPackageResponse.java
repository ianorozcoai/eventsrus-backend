package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.PackagePricingType;
import com.backend.eventsrus.enums.PackageType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorPackageResponse {

    private Long id;
    private String name;
    private String description;
    private PackageType packageType;
    private PackagePricingType pricingType;
    private BigDecimal price;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private boolean active;
}
