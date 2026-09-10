package com.backend.eventsrus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubmitReviewRequest {

    @Min(1)
    @Max(5)
    private int rating;

    @NotBlank
    @Size(max = 2000)
    private String comment;
}
