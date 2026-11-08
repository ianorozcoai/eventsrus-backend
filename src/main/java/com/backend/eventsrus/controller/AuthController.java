package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AuthResponse;
import com.backend.eventsrus.dto.GoogleAuthRequest;
import com.backend.eventsrus.dto.RefreshTokenRequest;
import com.backend.eventsrus.service.AuthService;
import com.backend.eventsrus.util.SignupSource;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(
            @Valid @RequestBody GoogleAuthRequest request,
            @RequestHeader(value = "X-Client-Platform", required = false) String platform,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ResponseEntity.ok(authService.authenticateWithGoogle(request, SignupSource.resolve(platform, userAgent)));
    }

    // No @PreAuthorize/bearer-auth needed here by design - this is exactly
    // the endpoint a client with an already-expired access token has to
    // reach, so it's covered by this controller's existing permitAll (see
    // SecurityConfig's "/api/v1/auth/**" rule). The raw refresh token
    // itself is the credential being checked (RefreshTokenService).
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
