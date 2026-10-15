package com.backend.eventsrus.dto;

import java.time.Instant;

/** When the account will be permanently scrubbed unless the user signs in again before then. */
public record AccountDeletionResponse(Instant scheduledFor) {}
