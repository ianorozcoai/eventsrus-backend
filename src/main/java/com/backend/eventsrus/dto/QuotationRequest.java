package com.backend.eventsrus.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QuotationRequest {

    @NotBlank
    private String plannerName;

    private LocalDate targetDate;

    @NotBlank
    private String message;

    /** Which of the vendor's packages this request is about - optional, and more than one may be flagged. */
    private List<Long> packageIds;
}
