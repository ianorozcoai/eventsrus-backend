package com.backend.eventsrus.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Budget is optional even when selecting the category - not every planner earmarks one up front. */
@Getter
@Setter
@NoArgsConstructor
public class UpdateSupplierTypeSelectionRequest {

    private boolean selected;

    @DecimalMin("0")
    private BigDecimal budget;
}
