package com.backend.eventsrus.service;

/** What a verified Apple identity token tells us. {@code subject} is Apple's stable user id for this app. */
public record AppleIdentity(String subject, String email, boolean emailVerified, boolean privateRelayEmail) {
}
