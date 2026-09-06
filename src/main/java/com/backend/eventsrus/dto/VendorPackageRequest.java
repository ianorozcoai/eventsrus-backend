package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.PackagePricingType;
import com.backend.eventsrus.enums.PackageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VendorPackageRequest {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private PackageType packageType;

    @NotNull
    private PackagePricingType pricingType;

    /** Used when pricingType == FIXED. */
    private BigDecimal price;

    /** Used when pricingType == RANGE. */
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}
