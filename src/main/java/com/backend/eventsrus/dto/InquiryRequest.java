package com.backend.eventsrus.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InquiryRequest {

    @NotBlank
    private String plannerName;

    private LocalDate targetDate;

    @NotBlank
    private String message;
}
