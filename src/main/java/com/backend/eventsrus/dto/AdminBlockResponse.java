package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.Role;
import java.time.Instant;

/** The admin's view of one block: who blocked whom, and when. */
public record AdminBlockResponse(
        Long id,
        Long blockerUserId,
        String blockerName,
        Role blockerRole,
        Long blockedUserId,
        String blockedName,
        Role blockedRole,
        Instant createdAt) {}
