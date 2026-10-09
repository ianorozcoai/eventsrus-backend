package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.DeviceTokenRequest;
import com.backend.eventsrus.service.DeviceTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Push-notification device registration - see DeviceToken's own doc comment. */
@RestController
@RequestMapping("/api/v1/users/me/device-tokens")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping
    public ResponseEntity<Void> register(@Valid @RequestBody DeviceTokenRequest request, Authentication authentication) {
        deviceTokenService.register(authentication.getName(), request.getToken(), request.getPlatform());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unregister(@Valid @RequestBody DeviceTokenRequest request, Authentication authentication) {
        deviceTokenService.unregister(authentication.getName(), request.getToken());
        return ResponseEntity.noContent().build();
    }
}
