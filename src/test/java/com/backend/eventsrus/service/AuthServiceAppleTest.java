package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.dto.AppleAuthRequest;
import com.backend.eventsrus.dto.AuthResponse;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.exception.InvalidAppleTokenException;
import com.backend.eventsrus.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The Apple endpoint's logic with a FAKE token verifier (no real Apple token needed). */
class AuthServiceAppleTest {

    private AppleIdentityTokenVerifier verifier;
    private UserService userService;
    private AppleSignInService appleSignInService;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private VendorPlanService vendorPlanService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        verifier = mock(AppleIdentityTokenVerifier.class);
        userService = mock(UserService.class);
        appleSignInService = mock(AppleSignInService.class);
        jwtService = mock(JwtService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        vendorPlanService = mock(VendorPlanService.class);
        authService = new AuthService(mock(GoogleTokenVerifierService.class), userService, jwtService, vendorPlanService,
                refreshTokenService, verifier, appleSignInService);
        when(jwtService.getExpirationMs()).thenReturn(3_600_000L);
        when(vendorPlanService.getEffectivePlan(any())).thenReturn(new VendorPlanService.EffectivePlan(null, null, false, false, false, null, null));
    }

    private static AppleAuthRequest request() {
        return new AppleAuthRequest("id-token", "raw-nonce", "auth-code", "Ian", "Orozco", "planner");
    }

    private User user() {
        User u = User.builder().id(5L).email("i@example.com").role(Role.PLANNER).build();
        when(jwtService.generateToken("i@example.com", Role.PLANNER)).thenReturn("jwt");
        when(refreshTokenService.issue(u)).thenReturn("refresh");
        return u;
    }

    @Test
    void aValidAppleSignInReturnsTheSameTokensAsGoogle() {
        AppleIdentity identity = new AppleIdentity("sub", "i@example.com", true, false);
        User u = user();
        when(verifier.verify("id-token", "raw-nonce")).thenReturn(identity);
        when(userService.findOrCreateFromApple(identity, "Ian", "Orozco", "planner")).thenReturn(u);

        AuthResponse response = authService.authenticateWithApple(request());

        assertThat(response.getToken()).isEqualTo("jwt");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(response.getEmail()).isEqualTo("i@example.com");
        verify(userService, never()).storeAppleRefreshToken(any(), any());
    }

    @Test
    void whenAppleKeysAreConfiguredTheEncryptedRefreshTokenIsStoredForDeletion() {
        AppleIdentity identity = new AppleIdentity("sub", "i@example.com", true, false);
        User u = user();
        when(verifier.verify(any(), any())).thenReturn(identity);
        when(userService.findOrCreateFromApple(any(), any(), any(), any())).thenReturn(u);
        when(appleSignInService.exchangeCodeForEncryptedRefreshToken("auth-code")).thenReturn("ENC");

        authService.authenticateWithApple(request());

        verify(userService).storeAppleRefreshToken(eq(u), eq("ENC"));
    }

    @Test
    void aBadTokenIsRejectedBeforeAnyAccountIsTouched() {
        when(verifier.verify(any(), any())).thenThrow(new InvalidAppleTokenException("bad"));

        assertThatThrownBy(() -> authService.authenticateWithApple(request())).isInstanceOf(InvalidAppleTokenException.class);
        verify(userService, never()).findOrCreateFromApple(any(), any(), any(), any());
    }
}
