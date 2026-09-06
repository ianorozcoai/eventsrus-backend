package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.LegalDocumentType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VendorLegalDocumentRequest {

    @NotNull
    private LegalDocumentType documentType;

    /** Mainly useful when documentType is OTHER, e.g. "Fire Safety Certificate". */
    private String label;
}
