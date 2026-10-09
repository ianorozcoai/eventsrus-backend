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
    private final RefreshTokenService refreshTokenService;

    public AuthResponse authenticateWithGoogle(GoogleAuthRequest request) {
        GoogleUserInfo googleUser = googleTokenVerifierService.verify(request.getIdToken());
        User user = userService.findOrCreateFromGoogle(googleUser, request.getIntent());
        return issueTokenFor(user);
    }

    /**
     * Redeems a refresh token for a fresh access/refresh token pair - see
     * RefreshTokenService#validateAndRotate for the single-use/rotation
     * behavior. Throws InvalidRefreshTokenException (-> 401) if it's
     * unknown, already used, or expired, same as a real re-login would be
     * required for.
     */
    public AuthResponse refresh(String rawRefreshToken) {
        User user = refreshTokenService.validateAndRotate(rawRefreshToken);
        return issueTokenFor(user);
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    public AuthResponse issueTokenFor(User user) {
        String token = jwtService.generateToken(user.getEmail(), user.getRole());
        String refreshToken = refreshTokenService.issue(user);
        VendorPlanService.EffectivePlan effectivePlan = vendorPlanService.getEffectivePlan(user.getId());

        return AuthResponse.builder()
                .id(user.getId())
                .token(token)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .role(user.getRole())
                .signupIntent(user.getSignupIntent())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .plan(effectivePlan.plan())
                .planExpiresAt(effectivePlan.expiresAt())
                .build();
    }
}
