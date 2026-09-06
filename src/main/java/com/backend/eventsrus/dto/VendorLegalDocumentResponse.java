package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.LegalDocumentType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorLegalDocumentResponse {

    private Long id;
    private LegalDocumentType documentType;
    private String label;
    private String url;
    private Instant createdAt;
}
