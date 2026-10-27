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
public class AppleAuthRequest {

    @NotBlank
    private String identityToken;

    /** The raw random value the app generated; the identity token carries its SHA-256 hex digest. */
    @NotBlank
    private String nonce;

    /** One-time code the backend may swap for a refresh token (only used once Apple keys are configured). */
    private String authorizationCode;

    /** Apple gives name and email to the app only the first time a person signs in. */
    private String givenName;
    private String familyName;

    /** "planner" or "vendor", same meaning as {@link GoogleAuthRequest#getIntent()}. */
    private String intent;
}
