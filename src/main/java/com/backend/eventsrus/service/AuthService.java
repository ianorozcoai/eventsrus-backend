package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.AuthResponse;
import com.backend.eventsrus.dto.GoogleAuthRequest;
import com.backend.eventsrus.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final UserService userService;
    private final JwtService jwtService;
    private final VendorPlanService vendorPlanService;

    public AuthResponse authenticateWithGoogle(GoogleAuthRequest request) {
        GoogleUserInfo googleUser = googleTokenVerifierService.verify(request.getIdToken());
        User user = userService.findOrCreateFromGoogle(googleUser, request.getIntent());
        return issueTokenFor(user);
    }

    public AuthResponse issueTokenFor(User user) {
        String token = jwtService.generateToken(user.getEmail(), user.getRole());
        VendorPlanService.EffectivePlan effectivePlan = vendorPlanService.getEffectivePlan(user.getId());

        return AuthResponse.builder()
                .id(user.getId())
                .token(token)
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .role(user.getRole())
                .firstName(user.getFirstName())
                .email(user.getEmail())
                .plan(effectivePlan.plan())
                .planExpiresAt(effectivePlan.expiresAt())
                .build();
    }
}
