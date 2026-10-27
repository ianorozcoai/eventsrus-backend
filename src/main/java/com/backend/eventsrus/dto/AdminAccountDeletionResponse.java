package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.Role;
import java.time.Instant;

/**
 * The admin's view of one account-deletion request. PENDING: inside the 30-day grace period (signing in again
 * cancels it); COMPLETED: the account has been scrubbed, so name and email are no longer available.
 */
public record AdminAccountDeletionResponse(
        Long userId,
        String status,
        Role role,
        String name,
        String email,
        Instant requestedAt,
        Instant scheduledDeletionAt,
        Instant deletedAt) {}
