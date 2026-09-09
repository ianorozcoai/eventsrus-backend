package com.backend.eventsrus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CoordinatorAskRequest {

    @NotBlank
    @Size(max = 1000)
    private String question;
}
