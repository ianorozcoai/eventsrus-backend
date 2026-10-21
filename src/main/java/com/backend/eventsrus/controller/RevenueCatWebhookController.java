package com.backend.eventsrus.controller;

import com.backend.eventsrus.config.RevenueCatProperties;
import com.backend.eventsrus.service.RevenueCatWebhookService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Receives RevenueCat's server-to-server events for Google Play
 * subscriptions. RevenueCat has no request signature; instead the dashboard
 * is set up to send a fixed value in the Authorization header, which we
 * compare against REVENUECAT_WEBHOOK_SECRET.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/revenuecat")
@RequiredArgsConstructor
public class RevenueCatWebhookController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final RevenueCatProperties revenueCatProperties;
    private final RevenueCatWebhookService revenueCatWebhookService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody String rawBody,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!isAuthorized(authorization)) {
            log.warn("Rejected RevenueCat webhook with missing/invalid secret");
            return ResponseEntity.status(401).build();
        }
        revenueCatWebhookService.handle(objectMapper.readTree(rawBody));
        return ResponseEntity.ok().build();
    }

    private boolean isAuthorized(String authorization) {
        String secret = revenueCatProperties.getWebhookSecret();
        if (secret == null || secret.isBlank() || authorization == null) {
            return false;
        }
        String provided = authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length())
                : authorization;
        return MessageDigest.isEqual(
                provided.trim().getBytes(StandardCharsets.UTF_8), secret.trim().getBytes(StandardCharsets.UTF_8));
    }
}
