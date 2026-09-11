package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AdminAccountResponse;
import com.backend.eventsrus.dto.AdminLoginRequest;
import com.backend.eventsrus.dto.CreateAdminAccountRequest;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.model.AdminAccount;
import com.backend.eventsrus.service.AdminAccountService;
import com.backend.eventsrus.service.JwtService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real admin login (replaces the old shared-secret "internal bridge") plus
 * admin-account management, both backed by AdminAccountService's DB-backed
 * accounts.
 *
 * /login is explicitly permitAll in SecurityConfig - a caller without a
 * token yet is exactly who needs to reach it, same reasoning the old bridge
 * used, except now it's a real per-admin username/password check instead of
 * one shared static key. Everything else here falls under SecurityConfig's
 * blanket /api/v1/admin/** -> hasRole("ADMIN") rule, so listing/creating
 * accounts already requires being logged in as an admin.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;
    private final JwtService jwtService;

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody AdminLoginRequest request) {
        if (!adminAccountService.authenticate(request.getUsername(), request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = jwtService.generateToken(request.getUsername(), Role.ADMIN);
        return ResponseEntity.ok(Map.of("token", token));
    }

    @GetMapping("/accounts")
    public List<AdminAccountResponse> list() {
        return adminAccountService.listAll().stream()
                .map(AdminAccountController::toResponse)
                .toList();
    }

    @PostMapping("/accounts")
    public AdminAccountResponse create(@Valid @RequestBody CreateAdminAccountRequest request) {
        return toResponse(adminAccountService.create(request.getUsername(), request.getPassword()));
    }

    private static AdminAccountResponse toResponse(AdminAccount account) {
        return AdminAccountResponse.builder()
                .username(account.getUsername())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
