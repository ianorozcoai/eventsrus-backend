package com.backend.eventsrus.dto;

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
public class QuotationStatusEventResponse {

    private Long id;
    private QuotationStatus fromStatus;
    private QuotationStatus toStatus;
    private Long changedByUserId;
    private String changedByName;
    private String reason;
    /** Only present on a QUOTE_SENT/REVISION_SENT event - the PDF the vendor sent at exactly this version. */
    private String pdfUrl;
    private Integer version;
    /** Only present on a QUOTE_SENT/REVISION_SENT/BOOKED event - the vendor's total at exactly this version. */
    private BigDecimal quotedAmount;
    /** Only present on a REQUEST_FOR_QUOTE/REVISION_REQUESTED event - the target date asked for at exactly this version. */
    private LocalDate targetDate;
    private List<String> packageNames;
    private Instant createdAt;
}
