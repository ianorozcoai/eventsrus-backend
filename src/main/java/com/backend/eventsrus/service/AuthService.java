package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.AuthResponse;
import com.backend.eventsrus.dto.GoogleAuthRequest;
import com.backend.eventsrus.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     *
     * @Transactional here (not just on issueTokenFor below) because
     * validateAndRotate is its own separately-@Transactional call: its
     * session closes the moment it returns, and the User proxy it hands
     * back stays bound to that dead session - issueTokenFor opening a new
     * transaction doesn't help, since a Hibernate proxy resolves lazy
     * fields through the session it was created in, not whatever's active
     * on the thread. Transactional's default REQUIRED propagation means
     * this method's transaction is what validateAndRotate actually joins,
     * keeping the User attached through both calls.
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        User user = refreshTokenService.validateAndRotate(rawRefreshToken);
        return issueTokenFor(user);
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    // refresh() hands this a User proxy from RefreshToken#user (a LAZY
    // @ManyToOne) whose owning transaction has already closed by the time
    // it gets here - @Transactional opens a fresh session so user.getEmail()
    // /getRole() below can actually resolve it, instead of throwing
    // LazyInitializationException ("no session"). Not readOnly: this also
    // covers the write in refreshTokenService#issue just below.
    @Transactional
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
