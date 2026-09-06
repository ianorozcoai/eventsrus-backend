package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.AmendmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class BookingAmendmentResponse {

    private Long id;
    private Long bookingId;
    private Long proposedByUserId;
    private String proposedByName;
    private BigDecimal newPrice;
    private Instant newEventDatetime;
    private String newAgreementDetails;
    private List<Long> newPackageIds;
    private List<String> newPackageNames;
    private String note;
    private AmendmentStatus status;
    private Instant createdAt;
    private Instant resolvedAt;
}
