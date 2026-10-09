package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * One device's Firebase Cloud Messaging registration token, belonging to
 * whichever user is currently signed in on it - see DeviceTokenService.
 * A push notification is addressed to a user by looking up every row here
 * for their id; actually sending one (not yet built) is a separate concern
 * from this registration resource.
 * token is unique rather than (user_id, token) - the same device can only
 * ever be signed in as one user at a time on this app, so a token
 * re-registered under a different user (a previous account signed out
 * without the app getting a chance to unregister it first, say) should
 * move to the new owner, not create a second row - see
 * DeviceTokenService#register's upsert.
 */
@Entity
@Table(name = "device_tokens")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class DeviceToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    /** "ANDROID" or "IOS" - free-form rather than an enum, since this only ever needs to round-trip, never branch on. */
    @Column(nullable = false, length = 16)
    private String platform;
}
