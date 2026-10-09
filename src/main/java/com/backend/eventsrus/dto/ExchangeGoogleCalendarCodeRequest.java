package com.backend.eventsrus.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** eventsrus-ui's native PKCE Google Calendar connect flow - see GoogleCalendarService#exchangeAuthorizationCode. */
@Getter
@Setter
@NoArgsConstructor
public class ExchangeGoogleCalendarCodeRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String codeVerifier;

    @NotBlank
    private String redirectUri;
}
