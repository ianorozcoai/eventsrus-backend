package com.backend.eventsrus.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenRequest {

    @NotBlank
    private String token;

    /** Only required on register (POST) - ignored on unregister (DELETE), which only needs the token itself. */
    private String platform;
}
