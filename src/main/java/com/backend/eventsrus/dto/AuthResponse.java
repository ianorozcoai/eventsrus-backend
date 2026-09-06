package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.Role;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AuthResponse {

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;

    private Role role;

    private String firstName;

    private String email;

    /** Null means the effective plan is FREE (no active PRO/PREMIUM subscription). */
    private PlanTier plan;

    private Instant planExpiresAt;
}
