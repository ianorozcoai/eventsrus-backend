package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.EventType;
import com.backend.eventsrus.enums.QuotationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class QuotationResponse {

    private Long id;
    private Long eventId;
    private String eventName;
    private EventType eventType;
    private Long vendorUserId;
    private String vendorBusinessName;
    private String vendorSlug;
    private Long plannerUserId;
    private LocalDate targetDate;
    private String requestMessage;
    private QuotationStatus status;
    private Integer version;
    private BigDecimal quotedAmount;
    private String pdfUrl;
    private Instant respondedAt;
    private Instant acceptedAt;
    private String paymentScreenshotUrl;
    private String paymentRejectionReason;
    private Instant createdAt;
    private List<Long> packageIds;
    private List<String> packageNames;
    private Instant declinedAt;
}
