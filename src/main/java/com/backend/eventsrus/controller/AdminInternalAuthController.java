package com.backend.eventsrus.controller;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bridges eventsrus-web's own lightweight admin login (BCrypt, in-memory,
 * no backend account of its own - see that project's AdminAccountService)
 * to a real ADMIN-role JWT, so the admin module can call the real
 * /api/v1/admin/** endpoints (SecurityConfig's JwtAuthenticationFilter
 * trusts the "role" claim straight from the token - no backing User row is
 * looked up per-request, so no real "admin" User needs to exist for this).
 *
 * Guarded by a shared secret (app.internal-admin-key), not a JWT - this
 * path is explicitly permitAll'd in SecurityConfig, since a caller without
 * a token yet is exactly who needs to reach it. Server-to-server only:
 * eventsrus-web calls this once per admin login and holds onto the
 * resulting token for that admin's session; it's never exposed to a
 * browser or to any other caller.
 */
@RestController
@RequiredArgsConstructor
public class AdminInternalAuthController {

    private static final String INTERNAL_ADMIN_SUBJECT = "admin@eventsrus.internal";

    private final JwtService jwtService;

    @Value("${app.internal-admin-key:}")
    private String internalAdminKey;

    @PostMapping("/api/v1/admin/internal-login")
    public ResponseEntity<?> internalLogin(@RequestHeader("X-Internal-Admin-Key") String providedKey) {
        if (internalAdminKey.isBlank() || !internalAdminKey.equals(providedKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = jwtService.generateToken(INTERNAL_ADMIN_SUBJECT, Role.ADMIN);
        return ResponseEntity.ok(java.util.Map.of("token", token));
    }
}
