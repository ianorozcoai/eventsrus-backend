package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.QuotationStatus;
import java.time.Instant;
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
    /** Only present on a RESPONDED event - the PDF the vendor sent at exactly this version. */
    private String pdfUrl;
    private Instant createdAt;
}
