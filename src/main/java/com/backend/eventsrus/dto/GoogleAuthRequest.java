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
public class GoogleAuthRequest {

    @NotBlank
    private String idToken;

    // "planner" or "vendor" - which login door was used. Optional: the
    // Flutter app doesn't send this yet, and a missing/blank value skips
    // the identity-lock check entirely rather than defaulting to either
    // side. See UserService#findOrCreateFromGoogle.
    private String intent;
}
