package com.backend.eventsrus.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BookingProposalRequest {

    private BigDecimal price;
    private Instant eventDatetime;
    private String agreementDetails;
}
