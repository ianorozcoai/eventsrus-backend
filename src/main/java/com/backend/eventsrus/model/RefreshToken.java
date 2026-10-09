package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A long-lived refresh token issued alongside a short-lived JWT access
 * token (see JwtService) so a mobile session can stay signed in for weeks
 * without re-authenticating, while the bearer token used on every API call
 * still rotates hourly - see RefreshTokenService.
 * Only tokenHash (SHA-256 of the raw token) is ever stored - the raw token
 * itself exists only in the response body at issue time and in the
 * client's own secure storage, never logged or persisted here. Single-use:
 * RefreshTokenService#validateAndRotate marks a row revoked the moment it's
 * redeemed, issuing a brand new row for the next refresh (rotation), so a
 * stolen-and-reused old token is rejected rather than silently accepted.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;
}
