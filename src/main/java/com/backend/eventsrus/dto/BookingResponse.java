package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class BookingResponse {

    private Long id;
    private Long eventId;
    private String eventName;
    private Long vendorUserId;
    private String vendorBusinessName;
    private String vendorSlug;
    private Long plannerUserId;
    private Long quotationId;
    private BigDecimal price;
    private Instant eventDatetime;
    private String agreementDetails;
    private BookingStatus status;
    private Instant proposedAt;
    private Instant respondedAt;
    private String paymentScreenshotUrl;
    private Instant paymentScreenshotUploadedAt;
    private Instant paymentAcknowledgedAt;
    private String paymentRejectionReason;
    private String invoiceUrl;
    private Instant invoiceUploadedAt;
    private Instant cancelledAt;
    private String cancellationReason;
    private Long cancelledByUserId;
}
