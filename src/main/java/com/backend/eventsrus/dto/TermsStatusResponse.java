package com.backend.eventsrus.dto;

/** Whether the signed-in person has accepted the current Terms, and the current version stamp. */
public record TermsStatusResponse(boolean accepted, String currentVersion) {}
