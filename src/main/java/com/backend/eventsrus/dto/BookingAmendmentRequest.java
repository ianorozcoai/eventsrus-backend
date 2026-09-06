package com.backend.eventsrus.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Every field but {@code note} is optional - null means "no change proposed to this field". */
@Getter
@Setter
@NoArgsConstructor
public class BookingAmendmentRequest {

    private BigDecimal newPrice;

    private Instant newEventDatetime;

    private String newAgreementDetails;

    private List<Long> newPackageIds;

    @NotBlank
    private String note;
}
