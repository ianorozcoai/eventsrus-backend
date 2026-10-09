package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.ExchangeGoogleCalendarCodeRequest;
import com.backend.eventsrus.dto.GoogleCalendarStatusResponse;
import com.backend.eventsrus.dto.SaveGoogleCalendarConnectionRequest;
import com.backend.eventsrus.service.GoogleCalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Data layer for eventsrus-web's flow (save/status/disconnect) - that app's
 * GoogleCalendarOAuthController owns the actual browser-based OAuth consent
 * redirect and calls save() here once it already has a refresh token in
 * hand. eventsrus-ui's native flow is different: it only ever hands this
 * controller a PKCE authorization code (exchange()), never a token or a
 * client secret - the code-for-token exchange happens inside
 * GoogleCalendarService, server-side, using the Desktop-app client's secret.
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

    @PostMapping("/exchange")
    public void exchange(@Valid @RequestBody ExchangeGoogleCalendarCodeRequest request, Authentication authentication) {
        googleCalendarService.exchangeAuthorizationCode(
                authentication.getName(), request.getCode(), request.getCodeVerifier(), request.getRedirectUri());
    }

    @DeleteMapping
    public void disconnect(Authentication authentication) {
        googleCalendarService.disconnect(authentication.getName());
    }
}
