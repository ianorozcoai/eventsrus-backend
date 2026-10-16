package com.backend.eventsrus.dto;

import java.time.Instant;

public record BlockedUserResponse(Long userId, String displayName, Instant blockedAt) {}
