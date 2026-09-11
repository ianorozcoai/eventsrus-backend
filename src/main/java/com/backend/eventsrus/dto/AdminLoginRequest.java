package com.backend.eventsrus.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminLoginRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
