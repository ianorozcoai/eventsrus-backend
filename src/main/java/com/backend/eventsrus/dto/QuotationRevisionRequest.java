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
public class QuotationRevisionRequest {

    private LocalDate targetDate;

    @NotBlank
    private String message;

    private List<Long> packageIds;
}
