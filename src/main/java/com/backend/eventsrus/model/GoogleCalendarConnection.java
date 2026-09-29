package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A vendor's opt-in connection to their own Google Calendar (see
 * GoogleCalendarService) - one row per vendor, created when they complete
 * the OAuth consent flow (see eventsrus-web's GoogleCalendarOAuthController)
 * and deleted outright on disconnect (no soft-disable state; reconnecting
 * just creates a fresh row). refreshTokenEncrypted is never logged or
 * returned from any API - GoogleCalendarClient decrypts it only to mint a
 * fresh access token when actually calling the Calendar API.
 */
@Entity
@Table(name = "google_calendar_connections")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class GoogleCalendarConnection extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendorProfile;

    @Column(name = "refresh_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String refreshTokenEncrypted;

    @Column(name = "google_calendar_id", nullable = false)
    private String googleCalendarId;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;
}
