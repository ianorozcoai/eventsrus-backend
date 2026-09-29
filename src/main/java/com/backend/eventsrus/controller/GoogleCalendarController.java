package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.GoogleCalendarStatusResponse;
import com.backend.eventsrus.dto.SaveGoogleCalendarConnectionRequest;
import com.backend.eventsrus.service.GoogleCalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Data layer only - the actual Google OAuth consent redirect (authorize +
 * callback, code-for-token exchange) lives entirely in eventsrus-web's
 * GoogleCalendarOAuthController, since that's what owns the vendor's
 * browser session and can redirect them back to a real page afterward. This
 * controller is just what that web-side flow calls once it already has a
 * refresh token in hand, plus status/disconnect for the Settings page.
 */
@RestController
@RequestMapping("/api/v1/vendors/me/google-calendar")
@RequiredArgsConstructor
public class GoogleCalendarController {

    private final GoogleCalendarService googleCalendarService;

    @GetMapping
    public GoogleCalendarStatusResponse status(Authentication authentication) {
        return googleCalendarService.getStatus(authentication.getName());
    }

    @PutMapping
    public void save(@Valid @RequestBody SaveGoogleCalendarConnectionRequest request, Authentication authentication) {
        googleCalendarService.saveConnection(authentication.getName(), request.getRefreshToken(), request.getGoogleCalendarId());
    }

    @DeleteMapping
    public void disconnect(Authentication authentication) {
        googleCalendarService.disconnect(authentication.getName());
    }
}
